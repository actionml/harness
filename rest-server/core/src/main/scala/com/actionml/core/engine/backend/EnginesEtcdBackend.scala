/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.core.engine.backend

import java.nio.charset.Charset
import java.time.Instant
import java.util.UUID

import com.actionml.core.config.{AppConfig, EtcdConfig}
import com.actionml.core.engine.EnginesBackend
import com.actionml.core.validate.{JsonSupport, ResourceNotFound, ValidRequestExecutionError, ValidateError, WrongParams}
import com.actionml.core.{HEnv, HIO, HQueue, harnessRuntime}
import com.typesafe.scalalogging.LazyLogging
import io.etcd.jetcd.Watch.Watcher
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.op.{Cmp, CmpTarget, Op}
import io.etcd.jetcd.options.{DeleteOption, GetOption, PutOption, WatchOption}
import io.etcd.jetcd.support.CloseableClient
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.clock.Clock
import zio.duration._
import zio.logging._
import zio.{CanFail, Cause, IO, Queue, Schedule, ZIO, ZQueue}

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

trait EnginesEtcdBackend extends EnginesBackend.Service with JsonSupport {

  import EnginesEtcdBackend._
  import com.actionml.core.utils.ZIOUtil.ImplicitConversions.ZioImplicits._

  def config: EtcdConfig

  private val etcdClient: ZIO[Logging with Clock, Throwable, Client] =
    IO.effect(Client.builder.endpoints(config.endpoints: _*).build)
      .catchAll { e =>
        log.error("Etcd client error", Cause.die(e)) *> etcdClient
      }.retry(etcdRetrySchedule)
  private val getKV: HIO[KV] = etcdClient.map(_.getKVClient)
    .catchAll { e =>
      log.error("Etcd client error", Cause.die(e)) *> getKV
    }.retry(etcdRetrySchedule)
  private val getWatch: HIO[Watch] = etcdClient.map(_.getWatchClient).retry(etcdRetrySchedule)
    .catchAll { e =>
      log.error("Etcd client error", Cause.die(e)) *> getWatch
    }.retry(etcdRetrySchedule)
  private val getLease: HIO[Lease] = etcdClient.map(_.getLeaseClient).retry(etcdRetrySchedule)
    .catchAll { e =>
      log.error("Etcd client error", Cause.die(e)) *> getLease
    }.retry(etcdRetrySchedule)

  private def mkHarnessIds: HIO[Queue[Long]] = {
    def runObserver(lease: Lease, q: Queue[Long]): ZIO[HEnv, ValidateError, Unit] = {
      for {
        leaseId <- lease.grant(5).toIO.map(_.getID)
        _ <- log.info(s"GOT HARNESS ID $leaseId")
        _ <- q.offer(leaseId)
        _ <- ZIO.effectAsync[HEnv, CloseableClient, Unit] { cb =>
          Try {
            lazy val keepAlive: CloseableClient = lease.keepAlive(leaseId, new StreamObserver[LeaseKeepAliveResponse] with LazyLogging {
              var currentId = 0L
              override def onCompleted(): Unit = {
                logger.info(s"Keep-alive completed for harness-$leaseId")
                cb(ZIO.fail(keepAlive))
              }
              override def onNext(resp: LeaseKeepAliveResponse): Unit = {
                harnessRuntime.unsafeRunSync(q.offer(resp.getID))
              }
              override def onError(e: Throwable): Unit = {
                logger.error("Etcd keep-alive error", e)
                cb(ZIO.fail(keepAlive))
              }
            })
            keepAlive
          }
        }.catchAll { ka =>
          Try(ka.close())
          runObserver(lease, q).delay(3.seconds)
        }.retry(Schedule.fixed(3.seconds))
      } yield ()
    }

    for {
      lease <- getLease
      q <- ZQueue.unbounded[Long]
      _ <- runObserver(lease, q).fork
    } yield q
  }

  override def modificationEventsQueue: HIO[ZQueue[Any, Any, Nothing, Nothing, String, (Long, String)]] = {
    for {
      idQ <- mkHarnessIds
      harnessId <- registerInstance(idQ)
      actionsQ <- Queue.sliding[String](1000)
      _ <- watchActions(actionsQ).forever.fork
      _ <- actionsQ.offer("")
    } yield actionsQ.map(harnessId -> _)
  }

  override def updateState(harnessId: Long, actionId: String): HIO[Unit] = {
    for {
      kv <- getKV
      instanceKey = s"${servicesPrefix}harness-$harnessId"
      opt = PutOption.newBuilder().withLeaseId(harnessId).build()
      _ <- kv.put(instanceKey, actionId, opt)
    } yield ()
  }


  private def registerInstance(harnessIds: Queue[Long]): HIO[Long] =
    for {
      kv <- getKV
      harnessId <- harnessIds.take
      instanceKey = s"${servicesPrefix}harness-$harnessId"
      _ <- log.info(s"Got harnessId=$harnessId")
      kOpt = PutOption.newBuilder().withLeaseId(harnessId).build()
      _ <- kv.put(instanceKey, "", kOpt)
    } yield harnessId

  private def watchActions(actionsQ: Queue[String]): HIO[Unit] =
    for {
      watch <- getWatch
      _ <- ZIO.effectAsync[HEnv, Watcher, Unit] { cb => Try {
        val wOpt = WatchOption.newBuilder().withPrefix(actionsPrefix).build()
        lazy val w: Watcher = watch.watch(actionsPrefix, wOpt, new Watch.Listener with LazyLogging {
          override def onNext(response: WatchResponse): Unit = cb {
            for {
              _ <- log.info(s"Engines actions detected ${response.getEvents.asScala.map(a => a.getKeyValue.getValue.toString(etcdCharset))}")
              actionIds = response.getEvents.asScala.map(_.getKeyValue.getKey.toString(etcdCharset).drop(actionsPrefix.length))
              _ <- actionsQ.offerAll(actionIds)
            } yield w
          }
          override def onError(e: Throwable): Unit = {
            logger.error("Actions watch error", e)
            cb(ZIO.fail(w))
          }
          override def onCompleted(): Unit = {
            logger.debug("Actions watcher completed")
            cb(ZIO.fail(w))
          }
        })
        w
      }}.onError(_.failureOption.fold(IO.unit)(w => IO.effect(Try(w.close())).ignore))
        .mapErrorCause(_ => Cause.fail(ValidRequestExecutionError()))
    } yield ()


  private def encode: EngineMetadata => String = toJsonString
  private def decode: String => HIO[EngineMetadata] = parseAndValidateIO[EngineMetadata](_)

  override def addEngine(id: String, data: EngineMetadata): HIO[Unit] = {
    import com.vladkopanev.zio.saga.Saga._
    val engineKey = s"$enginesPrefix$id"
    for {
      kv <- getKV
      actionId = UUID.randomUUID()
      actionKey = s"${actionsPrefix}$actionId"
      // todo: use transaction for action too
      _ <- kv.put(engineKey, encode(data)).toHIO
        .map(r => (r.getPrevKv.getModRevision, r.getPrevKv.getCreateRevision))
        .filterOrFail { case (0, 0) => true } (WrongParams(s"Engine $id already exists, use update"))
      waitForOthers = for {
        _ <- updateActionsInfo("add", actionKey)
        _ <- findUpdated(actionId)
      } yield ()
      removeEngineAndAction = kv.txn()
        // mod and create revisions equal to 0 means that no changes were made to the engine's meta information, so we can delete it (rollback)
        .If(new Cmp(engineKey, Cmp.Op.EQUAL, CmpTarget.modRevision(0)), new Cmp(engineKey, Cmp.Op.EQUAL, CmpTarget.createRevision(0)))
        .Then(Op.delete(engineKey, DeleteOption.newBuilder().build), Op.delete(actionKey, DeleteOption.newBuilder().build))
        .commit().toHIO.unit
      _ <- (waitForOthers compensate removeEngineAndAction).transact
    } yield ()
  }

  override def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      actionKey = s"${actionsPrefix}$actionId"
      _ <- updateActionsInfo("update", actionKey)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def deleteEngine(id: String): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.delete(enginesPrefix + id).unit
      actionId = UUID.randomUUID()
      actionKey = s"${actionsPrefix}$actionId"
      _ <- updateActionsInfo("delete", actionKey)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def findEngine(id: String): HIO[EngineMetadata] = {
    for {
      kv <- getKV
      r <- kv.get(id)
      e <- r.getKvs.asScala.headOption.fold[HIO[EngineMetadata]](IO.fail(ResourceNotFound(s"Engine $id not found"))) { e =>
        decode(e.getKey.toString(etcdCharset))
      }
    } yield e
  }

  override def listEngines: HIO[Iterable[EngineMetadata]] = {
    for {
      kv <- getKV
      response <- kv.get(enginesPrefix, kvPrefixOpt(enginesPrefix))
      result <- ZIO.collectAll(response.getKvs.asScala.toSeq.map { v =>
        decode(v.getValue.toString(etcdCharset))
      })
    } yield result
  }

  private def updateActionsInfo(action: String, actionKey: ByteSequence): HIO[Unit] = {
    for {
      kv <- getKV
      lease <- getLease
      id <- lease.grant(30)
      opt = PutOption.newBuilder().withLeaseId(id.getID).build
      _ <- kv.put(actionKey, s"""{"action":"$action","timestamp":"${Instant.now}"}""", opt)
    } yield ()
  }

  // waits for all instances to update their state
  private def findUpdated(actionUUID: UUID): HIO[Unit] = {
    val actionId = actionUUID.toString
    val watchAllOpt = WatchOption.newBuilder().build // fetch all from the beginning
    def waitForAction(instanceKey: ByteSequence, watch: Watch, start: Duration, timeout: Duration): HIO[Unit] = {
      ZIO.effectAsync[Any, ValidateError, Unit] { cb =>
        def cleanUp: Unit = Try(w.close())
        lazy val w = watch.watch(instanceKey, watchAllOpt, new Watch.Listener {
          override def onNext(response: WatchResponse): Unit = {
            if (response.getEvents.asScala.exists(_.getKeyValue.getValue.toString(etcdCharset) == actionId))
              cb(ZIO.unit)
            else if (now > (start + timeout)) {
              cleanUp
              cb(ZIO.fail(ValidRequestExecutionError()))
            }
          }
          override def onError(throwable: Throwable): Unit = {
            cleanUp
            cb(ZIO.fail(ValidRequestExecutionError()))
          }
          override def onCompleted(): Unit = {
            cleanUp
            cb(ZIO.fail(ValidRequestExecutionError()))
          }
        })
        w
      }
    }
    for {
      kv <- getKV
      watch <- getWatch
      timeout = Duration.fromScala(AppConfig.apply.etcdConfig.timeout)
      // fetch all registered harness instances
      instances <- kv.get(servicesPrefix, kvPrefixOpt(servicesPrefix))
      // wait for them to update
      _ <- ZIO.collectAllPar(instances.getKvs.asScala.toSeq.map { i =>
        if (i.getValue.toString(etcdCharset) == actionId) IO.unit
        else waitForAction(i.getKey, watch, now, timeout)
      })
    } yield ()
  }
}

object EnginesEtcdBackend {
  private implicit def toByteSequence(s: String): ByteSequence =
    if (s.isEmpty) ByteSequence.EMPTY
    else ByteSequence.from(s.getBytes)

  private val enginesPrefix = "/harness_meta_store/engines/"
  private val servicesPrefix = "/services/harness/instances/"
  private val actionsPrefix = "/services/harness/actions/"
  private val etcdCharset = Charset.forName("UTF-8")
  private def kvPrefixOpt(prefix: String) = GetOption.newBuilder().withPrefix(prefix).build
  private def watchPrefixOpt(prefix: String) = WatchOption.newBuilder().withPrefix(prefix).build

  private val etcdRetrySchedule = Schedule.fixed(3.second)

  private def now: Duration = Duration.fromInstant(Instant.now)
}
