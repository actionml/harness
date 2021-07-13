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

package com.actionml

import com.actionml.core.config.{AppConfig, StoreBackend}
import com.actionml.core.engine.EnginesBackend
import com.actionml.core.engine.backend.{EnginesEtcdBackend, EnginesMongoBackend, EtcdSupport, MongoStorageHelper}
import com.actionml.core.utils.HttpClient
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import com.typesafe.scalalogging.LazyLogging
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logging, log}
import zio.stream.ZStream
import zio.{Cause, Fiber, IO, Layer, Runtime, Task, ZIO, ZLayer, ZManaged}

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

package object core  extends LazyLogging {

  def drawActionML(): Unit = {
    val actionML =
      """
        |
        |               _   _             __  __ _
        |     /\       | | (_)           |  \/  | |
        |    /  \   ___| |_ _  ___  _ __ | \  / | |
        |   / /\ \ / __| __| |/ _ \| '_ \| |\/| | |
        |  / ____ \ (__| |_| | (_) | | | | |  | | |____
        | /_/    \_\___|\__|_|\___/|_| |_|_|  |_|______|
        |
        |    _    _
        |   | |  | |
        |   | |__| | __ _ _ __ _ __   ___  ___ ___
        |   |  __  |/ _` | '__| '_ \ / _ \/ __/ __|
        |   | |  | | (_| | |  | | | |  __/\__ \__ \
        |   |_|  |_|\__,_|_|  |_| |_|\___||___/___/
        |
        |
      """.stripMargin

    logger.info(actionML)
  }

  def drawInfo(title: String, dataMap: Seq[(String, Any)]): Unit = {
    val leftAlignFormat = "║ %-40s%-38s ║"

    val line = "═" * 80

    val preparedTitle = "║ %-78s ║".format(title)
    val data = dataMap.map {
      case (key, value) =>
        leftAlignFormat.format(key, value)
    } mkString "\n"

    logger.info(
      s"""
         |╔$line╗
         |$preparedTitle
         |$data
         |╚$line╝
         |""".stripMargin)

  }

  case class BadParamsException(message: String) extends Exception(message)

  type HEnv = EnginesBackend with HttpClient with Clock with Logging with Blocking with zio.system.System
  type HIO[A] = ZIO[HEnv, ValidateError, A]
  type HStream[A] = ZStream[HEnv, ValidateError, A]

  object HIO {
    def fromFuture[A](f: => Future[A]): HIO[A] = {
      Fiber.fromFuture(f).join
        .flatMapError { e =>
          log.error("Error in Future", Cause.die(e)).as(ValidRequestExecutionError())
        }
    }
  }

  val enginesBackend: Layer[Any, EnginesBackend] =
    ZLayer.fromManaged {
      ZManaged.make {
        AppConfig().enginesBackend match {
          case StoreBackend.etcd => IO.effect(new EnginesEtcdBackend)
          case _ => ZIO.effect(new EnginesMongoBackend(MongoStorageHelper.codecs){})
        }
      }(_ => IO.unit)
    }

  val harnessRuntime: Runtime.Managed[HEnv] = zio.Runtime.unsafeFromLayer {
    enginesBackend ++
    Slf4jLogger.make((_, s) => s) ++
    Clock.live ++
    Blocking.live ++
    zio.system.System.live ++
    HttpClient.live
  }

  object ValidateErrorImplicits {
    implicit def task2Hio[A](t: Task[A]): HIO[A] = t.flatMapError { e =>
      log.error("Task error", Cause.die(e)).as(ValidRequestExecutionError())
    }
  }
}
