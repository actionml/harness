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
import java.util.UUID

import com.actionml.core.config.EtcdConfig
import com.actionml.core.validate.{ResourceNotFound, ValidRequestExecutionError, ValidateError}
import com.actionml.core.{HEnv, HIO, HStream, harnessRuntime}
import com.typesafe.scalalogging.LazyLogging
import io.etcd.jetcd.Watch.Watcher
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.options.{GetOption, PutOption, WatchOption}
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.duration._
import zio.logging._
import zio.stream.ZStream
import zio.{Cause, Exit, IO, Managed, Queue, Schedule, ZIO}

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
      }
  private val getKV: HIO[KV] = etcdClient.map(_.getKVClient)
  private val getWatch: HIO[Watch] = etcdClient.map(_.getWatchClient)
  private val getLease: HIO[Lease] = etcdClient.map(_.getLeaseClient)

  private def mkHarnessId(): HStream[Long] = ZStream.fromEffect {
    (ZIO.effectAsync[HEnv, ValidateError, Long](cb => harnessRuntime.unsafeRunAsync_ {
      for {
        lease <- getLease
        id <- lease.grant(10).toIO.map(_.getID).map { leaseId =>
          def cleanup() = {
            Try(ka.close())
            cb(ZIO.interrupt)
          }
          lazy val ka: CloseableClient = lease.keepAlive(leaseId, new StreamObserver[LeaseKeepAliveResponse] with LazyLogging {
            override def onCompleted(): Unit = {
              logger.info(s"Keep-alive completed for harness-$leaseId")
              cleanup()
            }
            override def onNext(resp: LeaseKeepAliveResponse): Unit = cb(ZIO.succeed(resp.getID))
            override def onError(e: Throwable): Unit = {
              logger.error("Etcd keep-alive error", e)
              cleanup()
            }
          })
          ka
          leaseId
        }
      } yield id
    }).retry(Schedule.exponential(1.second)))
  }

  override def modificationEventsQueue: HStream[Unit] = {
    ZStream.succeed(()) merge startActionsWatcher(mkHarnessId())
  }

  private def startActionsWatcher(harnessIds: HStream[Long]): HStream[Unit] = {
    def startWatcher(watch: Watch, queue: Queue[Unit], kv: KV, instanceKey: ByteSequence, kvOptions: PutOption): HIO[Unit] = {
      def _onError(e: Throwable, logger: com.typesafe.scalalogging.Logger, prevWatcher: => Watcher): Unit = {
        logger.error("Actions watch error", e)
        prevWatcher.close()
      }
      def _onCompleted(logger: com.typesafe.scalalogging.Logger, prevWatcher: => Watcher): Unit = {
        logger.debug("Actions watcher completed")
        prevWatcher.close()
      }
      ZIO.effect {
        val wOpt = WatchOption.newBuilder().withPrefix(actionsPrefix).build()
        lazy val w: Watcher = watch.watch(actionsPrefix, wOpt, new Watch.Listener with LazyLogging {
          override def onNext(response: WatchResponse): Unit = harnessRuntime.unsafeRunSync {
            for {
              _ <- queue.offer(())
              _ <- kv.put(instanceKey, response.getEvents.asScala.head.getKeyValue.getKey.toString(etcdCharset).drop(actionsPrefix.length), kvOptions)
            } yield ()
          }
          override def onError(e: Throwable): Unit = _onError(e, logger, w)
          override def onCompleted(): Unit = _onCompleted(logger, w)
        })
      }.toHIO
    }
    harnessIds.flatMap { harnessId =>
      val instanceKey = s"${servicesPrefix}harness-$harnessId"
      ZStream.fromEffect {
        (for {
          _ <- log.info(s"GOT harnessId=$harnessId")
          notificationsQ <- Queue.sliding[Unit](1)
          kv <- getKV
          kOpt = PutOption.newBuilder().withLeaseId(harnessId).build()
          _ <- kv.put(instanceKey, "", kOpt)
          // subscribe to new actions
          watch <- getWatch
          _ <- startWatcher(watch, notificationsQ, kv, instanceKey, kOpt)
        } yield notificationsQ).retry(Schedule.exponential(1.second))
      }.flatMap(ZStream.fromQueue)
    }
  }

  protected def encode: D => String
  protected def decode: String => HIO[D]

  override def addEngine(id: String, data: D): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def updateEngine(id: String, data: D): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def deleteEngine(id: String): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.delete(enginesPrefix + id).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
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

  private def updateActionsInfo(actionId: UUID): HIO[Unit] = {
    for {
      kv <- getKV
      lease <- getLease
      id <- lease.grant(600)
      opt = PutOption.newBuilder().withLeaseId(id.getID).build
      _ <- kv.put(s"${actionsPrefix}$actionId", "", opt)
    } yield ()
  }

  // waits for all instances to update their state
  private def findUpdated(actionUuid: UUID): HIO[Unit] = {
    val actionId = actionUuid.toString
    val fetchAllOpt = GetOption.newBuilder().withMinModRevision(1).build // fetch all from the beginning
    def waitForAction(instanceKey: ByteSequence, kv: KV): HIO[Unit] = {
      for {
        olderKeys <- kv.get(instanceKey, fetchAllOpt)
        _ <- if (olderKeys.getKvs.asScala.exists(_.getValue.toString(etcdCharset) == actionId)) IO.unit
             else kv.get(instanceKey, fetchAllOpt)
               .toIO
               .repeat(Schedule.linear(500.milliseconds).untilInput(_.getKvs.asScala.exists(_.getValue.toString(etcdCharset) == actionId)))
               .timeout(3.seconds)
      } yield ()
    }
    for {
      kv <- getKV
      // fetch all registered harness instances
      instances <- kv.get(servicesPrefix, kvPrefixOpt(servicesPrefix))
      // wait for them to update
      _ <- ZIO.collectAll(instances.getKvs.asScala.map { i =>
        if (i.getValue.toString(etcdCharset) == actionId) IO.unit
        else waitForAction(i.getKey, kv)
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

}
