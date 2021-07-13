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

import com.actionml.core.config.AppConfig
import com.actionml.core.engine._
import com.actionml.core.engine.ActionNames._
import com.actionml.core.utils.HttpClient
import com.actionml.core.validate._
import com.actionml.core.{HEnv, HIO}
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.etcd.jetcd.Watch.Watcher
import io.etcd.jetcd._
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.options.{GetOption, PutOption, WatchOption}
import io.etcd.jetcd.support.CloseableClient
import io.etcd.jetcd.watch.WatchResponse
import io.grpc.stub.StreamObserver
import zio.duration._
import zio.logging._
import zio.{Cause, IO, Queue, ZIO}

import java.net.URL
import java.nio.charset.Charset
import java.time.Instant
import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

class EnginesEtcdBackend extends EnginesBackend.Service with JsonSupport {

  import EnginesEtcdBackend._
  import com.actionml.core.utils.ZIOUtil.HioImplicits._

  private val etcdClient = Client.builder.endpoints(config.etcdConfig.endpoints: _*).build
  private val kv = etcdClient.getKVClient
  private val watch = etcdClient.getWatchClient
  private val lease = etcdClient.getLeaseClient

  private def registerHarnessIds(leaseIdQueue: Queue[Long]): HIO[Unit] = for {
    leaseId <- lease.grant(5).toIO.map(_.getID)
    _ <- leaseIdQueue.offer(leaseId)
    instanceKey <- mkServiceKey
    kOpt = PutOption.newBuilder().withLeaseId(leaseId).build()
    _ <- kv.put(instanceKey, "", kOpt)
    _ <- if (config.jobs.jobControllerEnabled) hostPort.flatMap(h => kv.put(jcKey, h, kOpt))
         else IO.unit
    _ <- ZIO.effectAsync[HEnv, CloseableClient, Unit] { cb => Try {
      lazy val ka: CloseableClient = lease.keepAlive(leaseId, new StreamObserver[LeaseKeepAliveResponse] with LazyLogging {
        override def onCompleted(): Unit = {
          logger.warn(s"Keep-alive completed for harness-$leaseId")
          cb(ZIO.effect(ka.close()).ignore *> leaseIdQueue.shutdown)
        }
        override def onNext(resp: LeaseKeepAliveResponse): Unit = {
          logger.debug(s"NEXT ${resp.getID} (ttl=${resp.getTTL})")
        }
        override def onError(e: Throwable): Unit = {
          logger.error("Etcd keep-alive error", e)
          cb(ZIO.effect(ka.close()).ignore *> leaseIdQueue.shutdown)
        }
      })
      ka
    }}.mapError { ka =>
      Try(ka.close())
      ValidRequestExecutionError()
    }
  } yield ()

  override def watchActions(callback: Action => Unit): HIO[Unit] = for {
    leaseIdQ <- Queue.bounded[Long](1)
    actions <- kv.get(enginesPrefix, kvPrefixOpt(enginesPrefix))
      .map(_.getKvs.asScala.flatMap(s => decode(s.getValue)))
    _ = actions.foreach {
        case a: Add => callback(a)
        case u: Update => callback(u)
        case _ =>
      }
    _ <- registerHarnessIds(leaseIdQ).fork
    _ <- watchEngines(leaseIdQ, callback)
  } yield ()


  private def watchEngines(leaseQ: Queue[Long], callback: Action => Unit): HIO[Unit] = for {
    leaseId <- leaseQ.take
    r <- ZIO.runtime[HEnv]
    _ <- ZIO.effectAsync[HEnv, Watcher, Unit] { cb =>
      Try {
        lazy val w: Watcher = watch.watch(enginesPrefix, watchPrefixOpt(enginesPrefix), new Watch.Listener with LazyLogging {
          override def onNext(response: WatchResponse): Unit = r.unsafeRun { for {
            nodeKey <- mkServiceKey
            _ <- log.info(s"Engines actions detected ${response.getEvents.asScala.map(a => a.getKeyValue.getValue.toString(etcdCharset))}")
          } yield response.getEvents.asScala
            .flatMap(event => decode(event.getKeyValue.getValue.toString(etcdCharset)))
            .foreach { a =>
              callback(a)
              kv.put(nodeKey, encode(a), PutOption.newBuilder().withLeaseId(leaseId).build())
            }
          }
          override def onError(e: Throwable): Unit = cb(log.error("Actions watch error", Cause.fail(e)) *> leaseQ.shutdown)
          override def onCompleted(): Unit = cb(log.info("Actions watcher completed") *> leaseQ.shutdown)
        })
        w
      }
    }.onError(_.failureOption.fold(IO.unit)(w => IO.effect(w.close()).ignore))
      .mapErrorCause(_ => Cause.fail(ValidRequestExecutionError()))
  } yield ()

  private def encode(a: Action): String = a match {
    case Add(meta, id) => actionToJsonString(add, id, meta, a.timestamp)
    case Update(meta, id) => actionToJsonString(update, id, meta, a.timestamp)
    case Delete(meta, id) => actionToJsonString(delete, id, meta, a.timestamp)
  }
  private val decode: String => Option[Action] = s => {
    import io.circe.generic.auto._
    import io.circe.parser._

    parse(s) match {
      case Right(j) =>
        for {
          e <- j.hcursor.get[EngineMetadata]("config").right.toOption
          a <- j.hcursor.get[ActionName]("action").right.toOption
          id <- j.hcursor.get[String]("actionId").right.toOption
        } yield a match {
          case ActionNames.add => Add(e, id)
          case ActionNames.update => Update(e, id)
          case ActionNames.delete => Delete(e, id)
        }
      case _ => None
    }
  }

  override def addEngine(id: String, data: EngineMetadata): HIO[Unit] = updateEtcdEngine(Add(data)) >>= findUpdated

  override def updateEngine(id: String, data: EngineMetadata): HIO[Unit] = updateEtcdEngine(Update(data)) >>= findUpdated

  override def deleteEngine(id: String): HIO[Unit] = updateEtcdEngine(Delete(EngineMetadata(id, "", ""))) >>= findUpdated

  override def listNodes: HIO[List[NodeDescription]] = {
    def parseHostName: String => String = { case ServiceKeyPattern(host) => s"http://$host" }
    for {
      instanceKeys <- kv.get(servicesPrefix, kvPrefixOpt(servicesPrefix))
      descriptions <- ZIO.foreach(instanceKeys.getKvs.asScala.toList) { i =>
        val hostName = parseHostName(i.getKey)
        HttpClient.send(new URL(s"$hostName/engines"))
          .flatMap(parseAndValidateIO[List[EngineStatus]](_))
          .map(engines => NodeDescription(hostName, engines))
      }
    } yield descriptions
  }


  private val updateEtcdEngine: Action => HIO[String] = a => { a match {
    case Delete(meta, actionId) =>
      kv.put(mkEngineKey(meta.engineId), actionToJsonString(delete, actionId, meta, a.timestamp)).toIO
    case Add(meta, _) =>
      val key = mkEngineKey(meta.engineId)
      for {
        _ <- kv.get(key)
          .filterOrFail {
            _.getKvs
              .iterator()
              .asScala
              .toStream
              .headOption
              .forall(k => decode(k.getValue).exists {
                case Delete(_, _) => true
                case _ => false
              })
          }(WrongParams(s"Engine ${meta.engineId} already exists, use update"))
        _ <- kv.put(key, actionToJsonString(add, a.id, meta, a.timestamp))
      } yield ()
    case Update(meta, actionId) =>
      kv.put(mkEngineKey(meta.engineId), actionToJsonString(update, actionId, meta, a.timestamp)).toIO
  }}.as(a.id)

  private def actionToJsonString(actionName: ActionName, actionId: String, engineMetadata: EngineMetadata, timestamp: Instant): String = {
    s"""{"action":"$actionName","actionId":"$actionId","config":${engineMetadata.asJson.noSpaces},"timestamp":"$timestamp"}"""
  }

  // waits for all instances to update their state
  private def findUpdated(actionId: String): HIO[Unit] = {
    val watchAllOpt = WatchOption.newBuilder().build // fetch all from the beginning
    def waitForAction(instanceKey: ByteSequence, start: Duration, timeout: Duration): HIO[Unit] = {
      ZIO.effectAsync[Any, ValidateError, Unit] { cb =>
        def cleanUp(): Unit = Try(w.close())
        lazy val w = watch.watch(instanceKey, watchAllOpt, new Watch.Listener {
          override def onNext(response: WatchResponse): Unit = {
            if (response.getEvents.asScala.exists(event => decode(event.getKeyValue.getValue.toString(etcdCharset)).exists(_.id == actionId))) {
              cleanUp()
              cb(ZIO.unit)
            } else {
              cleanUp()
              cb(ZIO.fail(ValidRequestExecutionError()))
            }
          }
          override def onError(throwable: Throwable): Unit = {
            cleanUp()
            cb(ZIO.fail(ValidRequestExecutionError()))
          }
          override def onCompleted(): Unit = {
            cleanUp()
            cb(ZIO.unit)
          }
        })
        w
      }
    }
    val timeout = Duration.fromScala(AppConfig.apply.etcdConfig.timeout)
    for {
      // fetch all registered harness instances
      instances <- kv.get(servicesPrefix, kvPrefixOpt(servicesPrefix))
      // wait for them to update
      _ <- ZIO.foreachPar_(instances.getKvs.asScala)(i => waitForAction(i.getKey, now, timeout))
    } yield ()
  }
}

object EnginesEtcdBackend {
  private implicit def toByteSequence(s: String): ByteSequence =
    if (s.isEmpty) ByteSequence.EMPTY
    else ByteSequence.from(s.getBytes)

  private implicit def fromByteSequence: ByteSequence => String = _.toString(etcdCharset)

  private val enginesPrefix = "/harness_meta_store/engines/"
  private def mkEngineKey(engineId: String): ByteSequence = toByteSequence(s"$enginesPrefix$engineId")
  private val servicesPrefix = "/services/harness/instances/"
  private val config = AppConfig()
  private val port = config.restServer.port
  private val hostPort = AppConfig.hostName.map(n => s"$n:${port}")
  private val mkServiceKey = hostPort.map(n => s"${servicesPrefix}harness-$n")
    .mapError(_ => ValidRequestExecutionError())
  private def jcKey = s"${servicesPrefix}job-controller"
  private val ServiceKeyPattern = s"${servicesPrefix}harness-([a-zA-Z0-9-\\.:]*)".r
  private val etcdCharset = Charset.forName("UTF-8")
  private def kvPrefixOpt(prefix: String) = GetOption.newBuilder().withPrefix(prefix).build
  private def watchPrefixOpt(prefix: String) =
    WatchOption
      .newBuilder()
      .withPrevKV(true)
      .withPrefix(prefix)
      .build

  private def now: Duration = Duration.fromInstant(Instant.now)

  implicit val actionNameDecoder: Decoder[ActionNames.Value] = Decoder.enumDecoder(ActionNames)
  implicit val metaEncoder: Encoder[EngineMetadata] = deriveEncoder[EngineMetadata]
}
