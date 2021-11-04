/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

package com.actionml.admin

import akka.actor.ActorSystem
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.config.AppConfig
import com.actionml.core.engine.backend.EngineMetadata
import com.actionml.core.engine.{Add, Delete, Engine, Update}
import com.actionml.core.jobs.JobManager
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.search.elasticsearch.ElasticSearchSupport
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.validate._
import com.actionml.core.{HIO, drawActionML, _}
import com.typesafe.scalalogging.LazyLogging
import zio.duration._
import zio.logging.log
import zio.{IO, Schedule, ZIO}

import scala.util.{Properties, Try}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal


trait Administrator extends LazyLogging with JsonSupport {
  def system: ActorSystem
  def config: AppConfig
  private var engines = Map.empty[EngineMetadata, Engine]
  private val trainEC: ExecutionContext = system.dispatchers.lookup("train-dispatcher")
  import com.actionml.core.engine.EnginesBackend
  import com.actionml.core.engine.EnginesBackend._

  harnessRuntime.unsafeRunAsync {
    def update(meta: EngineMetadata): HIO[Unit] = newEngineInstanceIO(meta.engineFactory, meta.params).map { e =>
      engines = engines + (meta -> e)
    }
    watchActions {
      case Add(meta, _) => update(meta)
      case Update(meta, _) => update(meta)
      case Delete(meta, _) => IO.effect {
        val deletedEngine = engines.filter { case (EngineMetadata(id, _, _), _) => meta.engineId == id }
        Try(deletedEngine.foreach { case (_, e) =>
          e.destroy()
        })
        engines = engines -- deletedEngine.keys
      }.ignore
    }.retry(Schedule.linear(2.seconds)).forever
  }(_ => logger.error("Engines updates stopped"))

  drawActionML()

  private def newEngineInstanceIO(engineFactory: String, json: String): HIO[Engine] = IO.effect {
    Class.forName(engineFactory).getMethod("apply", classOf[String], classOf[Boolean]).invoke(null, json, java.lang.Boolean.FALSE).asInstanceOf[Engine]
  }.mapError { e =>
    val msg = s"Engine parse error with JSON: $json"
    logger.error(msg, e)
    ParseError(msg)
  }.flatMap(_.initAndGet(json, update = false))

  // instantiates all stored engine instances with restored state
  def init() = {
    // ask engines to init
    JobManager.abortExecutingJobs
    drawInfo("Harness Administrator initialized", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Number of Engines: ", engines.size),
      ("Engines: ", engines.keys)))
    this
  }

  def getEngine(engineId: String): Option[Engine] = engines.collectFirst {
    case (meta, e) if meta.engineId == engineId => e
  }

  def addEngine(json: String): HIO[Response] = {
    for {
      params <- parseAndValidateIO[GenericEngineParams](json)
      result <- getEngine(params.engineId).fold {
        newEngineInstanceIO(params.engineFactory, json) *> {
          for {
            _ <- EnginesBackend.addEngine(params.engineId, EngineMetadata(params.engineId, params.engineFactory, json))
            _ = logger.info(s"Engine for resource-id: ${params.engineId} with params $json initialized successfully")
          } yield Comment(s"EngineId: ${params.engineId} created")
        }
      } { _ =>
        logger.warn(s"Ignored, engine for resource-id: ${params.engineId} already exists, use update")
        IO.fail(WrongParams(s"Engine ${params.engineId} already exists, use update"))
      }
    } yield result
  }

  def updateEngine(json: String): HIO[Response] = {
    for {
      params <- parseAndValidateIO[GenericEngineParams](json)
      result <- getEngine(params.engineId)
        .fold[HIO[Response]](IO.fail(WrongParams(jsonComment(s"Unable to update Engine: ${params.engineId}, the engine does not exist")))) { existingEngine =>
          EnginesBackend
            .updateEngine(existingEngine.engineId, EngineMetadata(params.engineId, params.engineFactory, json))
            .flatMap(_ => existingEngine.init(json, update = true))
        }
    } yield result
  }

  def updateEngineWithImport(engineId: String, importPath: String): HIO[Response] = {
    getEngine(engineId).fold[HIO[Response]](
      IO.fail(ResourceNotFound(jsonComment(s"No Engine instance found for engineId: $engineId")))
    )(_.batchInputIO(importPath))
  }

  def updateEngineWithTrain(engineId: String): Validated[ValidateError, Response] = {
    getEngine(engineId).fold[Validated[ValidateError, Response]] {
      Invalid(WrongParams(jsonComment(s"Unable to train Engine: $engineId, the engine does not exist")))
    } { engine =>
      if (config.jobs.jobControllerEnabled) engine.train(trainEC)
      else Invalid(WrongParams("Train error"))
    }
  }

  def removeEngine(engineId: String): HIO[Response] = {
    getEngine(engineId).fold[HIO[Response]] {
      logger.warn(s"Cannot removeOne, non-existent engine for engineId: $engineId")
      IO.fail(WrongParams(jsonComment(s"Cannot removeOne non-existent engine for engineId: $engineId")))
    } { deadEngine =>
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      for {
        result <- deleteEngine(engineId).as(Comment(s"Engine instance for engineId: $engineId deleted and all its data"))
        _ <- IO.effect(deadEngine.destroy()).onError(log.error("Destroy engine error", _)).ignore
      } yield result
    }
  }

  def systemInfo(implicit ec: ExecutionContext): Future[Validated[ValidateError, SystemInfo]] = {
    logger.trace("Getting Harness system info")
    val mongoCheck = MongoStorage.healthCheck
    val esCheck = ElasticSearchSupport.healthCheck()
    (for {
      mongoStatus <- mongoCheck
      esStatus <- esCheck
    } yield Valid(SystemInfo(
      buildVersion = com.actionml.admin.BuildInfo.version,
      gitBranch = Properties.envOrElse("BRANCH", "No git branch (BRANCH) detected in env." ),
      gitHash = Properties.envOrElse("GIT_HASH", "No git short commit number (GIT_HASH) detected in env." ),
      harnessURI = Properties.envOrElse("HARNESS_URI", "No HARNESS_URI set, using host and port" ),
      mongoURI = Properties.envOrElse("MONGO_URI", "ERROR: No URI set" ),
      elasticsearchURI = Properties.envOrElse("ELASTICSEARCH_URI", "No URI set,using host, port and protocol"),
      mongoStatus = mongoStatus.toString,
      elasticsearchStatus = esStatus.toString
    ))).recover {
      case NonFatal(e) =>
        logger.error("System info error", e)
        Invalid(ValidRequestExecutionError("System info error"))
    }
  }


  def statuses(): HIO[List[Response]] = {
    logger.trace("Getting status for all Engines")
    ZIO.collectAllPar(engines.map(_._2.status()).toList)
  }

  def status(resourceId: String): HIO[Response] = {
    engines.collectFirst {
      case (meta, e) if meta.engineId == resourceId =>
        logger.trace(s"Getting status for $resourceId")
        e.status()
    }.getOrElse {
      logger.error(s"Non-existent engine-id: $resourceId")
      IO.fail(WrongParams(jsonComment(s"Non-existent engine-id: $resourceId")))
    }
  }

  def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    getEngine(engineId).map { engine =>
      engine.cancelJob(engineId, jobId)
    }.getOrElse {
      logger.error(s"Non-existent engine-id: $engineId")
      Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $engineId")))
    }
  }
}

case class EnginesStatuses(statuses: List[Response]) extends Response

case class SystemInfo(
  buildVersion: String,
  gitBranch: String,
  gitHash: String,
  harnessURI: String,
  mongoURI: String,
  mongoStatus: String,
  elasticsearchURI: String,
  elasticsearchStatus: String
) extends Response
