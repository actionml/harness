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
import com.actionml.core.validate.{ResourceNotFound, ValidRequestExecutionError, ValidateError}
import com.actionml.core.{HEnv, HIO, harnessRuntime}
import com.typesafe.scalalogging.LazyLogging
import io.etcd.jetcd.Watch.Watcher
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.options.{GetOption, PutOption, WatchOption}
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.duration._
import zio.logging._
import zio.{Cause, IO, Queue, Schedule, UIO, ZIO}

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
      }.retry(Schedule.linear(5.seconds))
  private val getKV: HIO[KV] = etcdClient.map(_.getKVClient).retry(Schedule.linear(5.second))
  private val getWatch: HIO[Watch] = etcdClient.map(_.getWatchClient).retry(Schedule.linear(5.second))
  private val getLease: HIO[Lease] = etcdClient.map(_.getLeaseClient).retry(Schedule.linear(5.second))

  private def mkHarnessId: HIO[Queue[Long]] = {
    def keepAlive(q: Queue[Long]): HIO[Unit] =
      for {
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
    for {
      q <- Queue.sliding[Long](1)
      _ <- keepAlive(q).retry(Schedule.linear(3.seconds)).fork
    } yield q
  }

  override def modificationEventsQueue: HIO[Queue[Unit]] = mkHarnessId.flatMap(q => startActionsWatcher(q))

  private def startActionsWatcher(harnessIds: Queue[Long]): HIO[Queue[Unit]] = {
    def watchActions(notificationsQ: Queue[Unit]): HIO[Unit] =
      for {
        watch <- getWatch
        kv <- getKV
        harnessId <- harnessIds.take
        instanceKey = s"${servicesPrefix}harness-$harnessId"
        _ <- log.info(s"Got harnessId=$harnessId")
        kOpt = PutOption.newBuilder().withLeaseId(harnessId).build()
        _ <- kv.put(instanceKey, "", kOpt)
        // subscribe to new actions
        _ <- startWatcher(watch, notificationsQ, kv, instanceKey, kOpt).retry(Schedule.linear(3.second)).forever.fork
      } yield ()
    def startWatcher(watch: Watch, queue: Queue[Unit], kv: KV, instanceKey: ByteSequence, kvOptions: PutOption): HIO[Any] =
      ZIO.effectAsync[HEnv, ValidateError, Any] { cb =>
        val wOpt = WatchOption.newBuilder().withPrefix(actionsPrefix).build()
        lazy val w: Watcher = watch.watch(actionsPrefix, wOpt, new Watch.Listener with LazyLogging {
          override def onNext(response: WatchResponse): Unit = cb {
            for {
              _ <- log.info(s"Engines actions detected ${response.getEvents.asScala}")
              events = response.getEvents.asScala.map(_.getKeyValue.getKey.toString(etcdCharset).drop(actionsPrefix.length)).toList
              _ <- ZIO.collectAllPar(events.map(kv.put(instanceKey, _, kvOptions).toIO))
              _ <- queue.offer(())
            } yield ()
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
      }

    for {
      notificationsQ <- Queue.sliding[Unit](1)
      _ <- notificationsQ.offer ()
      _ <- watchActions(notificationsQ).forever.fork
    } yield notificationsQ
  }

  protected def encode: D => String
  protected def decode: String => HIO[D]

  override def addEngine(id: String, data: D): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo("add", actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def updateEngine(id: String, data: D): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo("update", actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def deleteEngine(id: String): HIO[Unit] = {
    for {
      kv <- getKV
      _ <- kv.delete(enginesPrefix + id).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo("delete", actionId)
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

  private def updateActionsInfo(action: String, actionId: UUID): HIO[Unit] = {
    for {
      kv <- getKV
      lease <- getLease
      id <- lease.grant(600)
      opt = PutOption.newBuilder().withLeaseId(id.getID).build
      _ <- kv.put(s"${actionsPrefix}$actionId", s"""{"action":"$action","timestamp":"${Instant.now}"}""", opt)
    } yield ()
  }

  // waits for all instances to update their state
  private def findUpdated(actionUUID: UUID): HIO[Unit] = {
    val actionId = actionUUID.toString
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
