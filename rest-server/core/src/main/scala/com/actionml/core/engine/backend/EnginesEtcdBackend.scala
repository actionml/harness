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
import com.typesafe.scalalogging.LazyLogging
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.options.{GetOption, PutOption, WatchOption}
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.duration._
import zio.logging._
import zio.logging.slf4j._
import zio.{Cause, IO, Queue, Schedule, ZIO, ZLayer}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

trait EnginesEtcdBackend[D] extends EnginesBackend[String, D, String] {
  import EnginesEtcdBackend._
  import com.actionml.core.utils.ZIOUtil.ImplicitConversions.ZioImplicits._
  protected def config: EtcdConfig

  private val etcdClient: IO[ValidateError, Client] =
    IO.effect(Client.builder.endpoints(config.endpoints: _*).build)
      .flatMapError { e =>
        log.error("Etcd client error", Cause.die(e))
          .provideLayer { Slf4jLogger.make{(context, message) =>
            val logFormat = "[correlation-id = %s] %s"
            val correlationId = LogAnnotation.CorrelationId.render(context.get(LogAnnotation.CorrelationId))
            logFormat.format(correlationId, message)
          }}
          .map(_ => ValidRequestExecutionError())
      }
  private val getKV = etcdClient.map(_.getKVClient)
  private val getWatch = etcdClient.map(_.getWatchClient)
  private val getLease = etcdClient.map(_.getLeaseClient)
  private var harnessId: Long = _
  private var keepAliveClient: CloseableClient = _
  private def observer(q: Queue[Unit]) = new StreamObserver[LeaseKeepAliveResponse] with LazyLogging {
    override def onCompleted(): Unit = {
      q.offer(())
    }
    override def onNext(resp: LeaseKeepAliveResponse): Unit = harnessId = resp.getID
    override def onError(e: Throwable): Unit = {
      logger.error("Etcd keep-alive error", e)
    }
  }

  override def changesQueue: IO[ValidateError, Queue[Unit]] = {
    for {
      // "register" as an instance with lease id, keep-alive
      lease <- getLease
      _ <- cleanPreviousRegistration(lease)
      resp <- lease.grant(10).toIO
      leaseId = resp.getID
      instanceKey = s"${servicesPrefix}harness-$leaseId"
      kv <- getKV
      kOpt = PutOption.newBuilder().withLeaseId(leaseId).build()
      _ <- kv.put(instanceKey, "", kOpt)
      // subscribe to new actions
      watch <- getWatch
      q <- Queue.unbounded[Unit]
      _ = startActionsWatcher(watch, q, kv, instanceKey, kOpt)
//      _ <- log.info(s"GOT harnessId=$leaseId")
    } yield {
      println(s"GOT harnessId=$leaseId")
      keepAliveClient = lease.keepAlive(leaseId, observer(q))
      harnessId = leaseId
      q
    }
  }

  private def startActionsWatcher(watch: Watch, queue: Queue[Unit], kv: KV, instanceKey: ByteSequence, kvOptions: PutOption): Unit = {
    val wOpt = WatchOption.newBuilder().withPrefix(actionsPrefix).build()
    def _onNext(response: WatchResponse): Unit = run {
      for {
        _ <- queue.offer(())
        _ <- kv.put(instanceKey, response.getEvents.asScala.head.getKeyValue.getKey.toString(etcdCharset).drop(actionsPrefix.length), kvOptions)
      } yield ()
    }
    def _onError(e: Throwable, logger: com.typesafe.scalalogging.Logger): Unit = {
      logger.error("Actions watch error", e)
      startWatcher()
    }
    def _onCompleted(logger: com.typesafe.scalalogging.Logger): Unit = {
      logger.debug("Actions watcher completed")
      startWatcher()
    }
    def mkActionsListener = new Watch.Listener with LazyLogging {
      override def onNext(response: WatchResponse): Unit = _onNext(response)
      override def onError(e: Throwable): Unit = _onError(e, logger)
      override def onCompleted(): Unit = _onCompleted(logger)
    }
    def startWatcher() = watch.watch(actionsPrefix, wOpt, mkActionsListener)

    startWatcher()
  }

  private def cleanPreviousRegistration(lease: Lease): IO[ValidateError, Unit] = {
    for {
      _ <- lease.revoke(harnessId).ignore.fork
      _ <- IO.effect(keepAliveClient.close()).ignore.fork
    } yield ()
  }

  protected def encode: D => String
  protected def decode: String => IO[ValidateError, D]

  override def addEngine(id: String, data: D): IO[ValidateError, Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def updateEngine(id: String, data: D): IO[ValidateError, Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(enginesPrefix + id, encode(data)).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def deleteEngine(id: String): IO[ValidateError, Unit] = {
    for {
      kv <- getKV
      _ <- kv.delete(enginesPrefix + id).unit
      actionId = UUID.randomUUID()
      _ <- updateActionsInfo(actionId)
      _ <- findUpdated(actionId)
    } yield ()
  }

  override def findEngine(id: String): IO[ValidateError, D] = {
    for {
      kv <- getKV
      r <- kv.get(id)
      e <- r.getKvs.asScala.headOption.fold[IO[ValidateError, D]](IO.fail(ResourceNotFound(s"Engine $id not found"))) { e =>
        decode(e.getKey.toString(etcdCharset))
      }
    } yield e
  }

  override def listEngines: IO[ValidateError, Iterable[D]] = {
    for {
      kv <- getKV
      response <- kv.get(enginesPrefix, kvPrefixOpt(enginesPrefix))
      result <- ZIO.collectAll(response.getKvs.asScala.map { v =>
        decode(v.getValue.toString(etcdCharset))
      })
    } yield result
  }

  private def updateActionsInfo(actionId: UUID): IO[ValidateError, Unit] = {
    for {
      kv <- getKV
      _ <- kv.put(s"${actionsPrefix}$actionId", "")
    } yield ()
  }

  // waits for all instances to update their state
  private def findUpdated(actionUuid: UUID): IO[ValidateError, Unit] = {
    val actionId = actionUuid.toString
    val fetchAllOpt = GetOption.newBuilder().withMinModRevision(1).build // fetch all from the beginning
    def waitForAction(instanceKey: ByteSequence, kv: KV): IO[ValidateError, Unit] = {
      (for {
        olderKeys <- kv.get(instanceKey, fetchAllOpt)
        _ <- if (olderKeys.getKvs.asScala.exists(_.getValue.toString(etcdCharset) == actionId)) IO.unit
             else kv.get(instanceKey, fetchAllOpt)
               .toIO
               .repeat(Schedule.linear(500.milliseconds).untilInput(_.getKvs.asScala.exists(_.getValue.toString(etcdCharset) == actionId)))
               .timeout(3.seconds)
      } yield ()).provideLayer(zio.clock.Clock.live)
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

  private val rt = zio.Runtime.unsafeFromLayer(ZLayer.succeed())
  private def run(io: IO[ValidateError, Unit]) = {
    rt.unsafeRunSync(io)
  }
}
