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

import com.actionml.core.config.EtcdConfig
import com.actionml.core.validate.{ResourceNotFound, ValidRequestExecutionError, ValidateError, WrongParams}
import com.actionml.core.{HEnv, HIO, HQueue}
import com.typesafe.scalalogging.LazyLogging
import io.etcd.jetcd.Watch.Watcher
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.op.{Cmp, CmpTarget, Op}
import io.etcd.jetcd.options.{DeleteOption, GetOption, PutOption, WatchOption}
import io.etcd.jetcd.support.CloseableClient
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.duration._
import zio.logging._
import zio.{Cause, IO, Queue, Schedule, ZIO}

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

trait EnginesEtcdBackend[D] extends EnginesBackend[String, D, String] {

  import EnginesEtcdBackend._
  import com.actionml.core.utils.ZIOUtil.ImplicitConversions.ZioImplicits._

  protected def config: EtcdConfig

  private val etcdClient: HIO[Client] =
    IO.effect(Client.builder.endpoints(config.endpoints: _*).build)
      .flatMapError { e =>
        log.error("Etcd client error", Cause.die(e))
          .map(_ => ValidRequestExecutionError())
      }.retry(etcdRetrySchedule)
  private val getKV: HIO[KV] = etcdClient.map(_.getKVClient).retry(etcdRetrySchedule)
  private val getWatch: HIO[Watch] = etcdClient.map(_.getWatchClient).retry(etcdRetrySchedule)
  private val getLease: HIO[Lease] = etcdClient.map(_.getLeaseClient).retry(etcdRetrySchedule)

  private def mkHarnessId: HIO[Queue[Long]] = {
    for {
      q <- Queue.sliding[Long](1)
      keepAlive = for {
        lease <- getLease
        leaseId <- lease.grant(5).toIO.map(_.getID)
        _ <- q.offer(leaseId)
        _ <- ZIO.effectAsync[HEnv, ValidateError, Any] { cb =>
          lazy val kaClient: CloseableClient = lease.keepAlive(leaseId, new StreamObserver[LeaseKeepAliveResponse] with LazyLogging {
            override def onCompleted(): Unit = {
              logger.info(s"Keep-alive completed for harness-$leaseId")
              Try(kaClient.close())
              cb(ZIO.fail(ValidRequestExecutionError()))
            }
            override def onNext(resp: LeaseKeepAliveResponse): Unit = {}
            override def onError(e: Throwable): Unit = {
              logger.error("Etcd keep-alive error", e)
              Try(kaClient.close())
              cb(ZIO.fail(ValidRequestExecutionError()))
            }
          })
          kaClient
        }
      } yield ()
      _ <- keepAlive.fork
    } yield q
  }

  override def modificationEventsQueue: HIO[HQueue[String, (Long, String)]] = {
    for {
      harnessIds <- mkHarnessId
      actionsQ <- Queue.sliding[String](1000)
      _ <- watchActions(actionsQ).forever.fork
      harnessId <- harnessIds.take
      _ <- registerInstance(harnessId)
      _ <- actionsQ.offer("")
    } yield actionsQ.map(harnessId -> _)
  }.retry(Schedule.linear(2.seconds))

  override def updateState(harnessId: Long, actionId: String): HIO[Unit] = {
    for {
      kv <- getKV
      instanceKey = s"${servicesPrefix}harness-$harnessId"
      opt = PutOption.newBuilder().withLeaseId(harnessId).build()
      _ <- kv.put(instanceKey, actionId, opt)
    } yield ()
  }


  private def registerInstance(harnessId: Long): HIO[Unit] =
    for {
      kv <- getKV
      instanceKey = s"${servicesPrefix}harness-$harnessId"
      _ <- log.info(s"Got harnessId=$harnessId")
      kOpt = PutOption.newBuilder().withLeaseId(harnessId).build()
      _ <- kv.put(instanceKey, "", kOpt)
    } yield ()

  private def watchActions(actionsQ: Queue[String]): HIO[Unit] =
    for {
      watch <- getWatch
      watcher <- ZIO.effectAsync[HEnv, ValidateError, Watcher] { cb => Try {
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
            Try(w.close())
            cb(ZIO.fail(ValidRequestExecutionError()))
          }
          override def onCompleted(): Unit = {
            logger.debug("Actions watcher completed")
            Try(w.close())
            cb(ZIO.fail(ValidRequestExecutionError()))
          }
        })
        w
      }}
    } yield ()

  protected def encode: D => String
  protected def decode: String => HIO[D]

  override def addEngine(id: String, data: D): HIO[Unit] = {
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

  override def updateEngine(id: String, data: D): HIO[Unit] = {
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

  override def findEngine(id: String): HIO[D] = {
    for {
      kv <- getKV
      r <- kv.get(id)
      e <- r.getKvs.asScala.headOption.fold[HIO[D]](IO.fail(ResourceNotFound(s"Engine $id not found"))) { e =>
        decode(e.getKey.toString(etcdCharset))
      }
    } yield e
  }

  override def listEngines: HIO[Iterable[D]] = {
    for {
      kv <- getKV
      response <- kv.get(enginesPrefix, kvPrefixOpt(enginesPrefix))
      result <- ZIO.collectAll(response.getKvs.asScala.map { v =>
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
      timeout = 4.seconds
      // fetch all registered harness instances
      instances <- kv.get(servicesPrefix, kvPrefixOpt(servicesPrefix))
      // wait for them to update
      _ <- ZIO.collectAllPar(instances.getKvs.asScala.map { i =>
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

  private val etcdRetrySchedule = Schedule.linear(3.second)

  private def now: Duration = Duration.fromInstant(Instant.now)
}
