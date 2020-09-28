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
import cats.data.Validated.Invalid
import com.actionml.core.engine.Engine
import com.actionml.core.engine.backend.EngineMetadata
import com.actionml.core.jobs.JobManager
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.validate._
import com.actionml.core.{HIO, drawActionML, _}
import com.typesafe.scalalogging.LazyLogging
import zio.logging.log
import zio.stream.ZStream
import zio.{IO, ZIO}

import scala.concurrent.ExecutionContext
import scala.util.Properties


trait Administrator extends LazyLogging with JsonSupport {
  def system: ActorSystem
  private var engines = Map.empty[String, Engine]
  private val trainEC: ExecutionContext = system.dispatchers.lookup("train-dispatcher")
  import com.actionml.core.engine.EnginesBackend
  import com.actionml.core.engine.EnginesBackend._

  harnessRuntime.unsafeRunAsync {
    ZStream.fromEffect(modificationEventsQueue)
      .flatMap(q => ZStream.fromQueue(q))
      .foreach { case (harnessId, actionId) =>
        for {
          l <- listEngines
          _ <- ZIO.foreach(l)(e => newEngineInstanceIO(e.engineFactory, e.params))
            .map(l => engines = l.map(e => e.engineId -> e).toMap)
          _ <- updateState(harnessId, actionId).ignore
          _ <- log.info(s"Engines updated at harness-$harnessId after action $actionId")
        } yield ()
      }
  }(_ => logger.error("Engines updates stopped"))

  drawActionML()

  private def newEngineInstance(engineFactory: String, json: String): Engine = {
    Class.forName(engineFactory).getMethod("apply", classOf[String], classOf[Boolean]).invoke(null, json, java.lang.Boolean.TRUE).asInstanceOf[Engine]
  }
  private def newEngineInstanceIO(engineFactory: String, json: String): HIO[Engine] = {
    val error = ParseError(jsonComment(s"Unable to create Engine the config JSON seems to be in error"))
    IO.effect(newEngineInstance(engineFactory, json))
      .mapError { e =>
        logger.error("Engine creation error", e)
        error
      }.filterOrFail(_ != null)(error)
  }

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

  def getEngine(engineId: String): Option[Engine] = engines.get(engineId)

  // engine management
  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): HIO[Response] = {
    for {
      params <- parseAndValidateIO[GenericEngineParams](json)
      result <- getEngine(params.engineId).fold {
        newEngineInstanceIO(params.engineFactory, json).flatMap(_ =>
          EnginesBackend.addEngine(params.engineId, EngineMetadata(params.engineId, params.engineFactory, json)).map { _ =>
            logger.debug(s"Engine for resource-id: ${params.engineId} with params $json initialized successfully")
            Comment(s"EngineId: ${params.engineId} created")
          }
        )
      } { _ =>
        logger.warn(s"Ignored, engine for resource-id: ${params.engineId} already exists, use update")
        IO.fail(WrongParams(s"Engine ${params.engineId} already exists, use update"))
      }
    } yield result
  }

  def updateEngine(json: String): HIO[Response] = {
    import com.actionml.core.utils.ZIOUtil.ValidatedImplicits._
    for {
      params <- parseAndValidateIO[GenericEngineParams](json)
      result <- getEngine(params.engineId)
        .fold[HIO[Response]](IO.fail(WrongParams(jsonComment(s"Unable to update Engine: ${params.engineId}, the engine does not exist")))) { existingEngine =>
          (EnginesBackend.updateEngine(existingEngine.engineId, EngineMetadata(params.engineId, params.engineFactory, json)) *>
            existingEngine.init(json, update = true)).mapError(e => ValidRequestExecutionError())
        }
    } yield result
  }

  def updateEngineWithImport(engineId: String, importPath: String): Validated[ValidateError, Response] = {
    getEngine(engineId).fold[Validated[ValidateError, Response]](
      Invalid(ResourceNotFound(jsonComment(s"No Engine instance found for engineId: $engineId")))
    )(_.batchInput(importPath))
  }

  def updateEngineWithTrain(engineId: String): Validated[ValidateError, Response] = {
    val eid = getEngine(engineId)
    if (eid.isDefined) {
      eid.get.train(trainEC)
    } else {
      Invalid(WrongParams(jsonComment(s"Unable to train Engine: $engineId, the engine does not exist")))
    }
  }

  def removeEngine(engineId: String): HIO[Response] = {
    getEngine(engineId).fold[HIO[Response]] {
      logger.warn(s"Cannot removeOne, non-existent engine for engineId: $engineId")
      IO.fail(WrongParams(jsonComment(s"Cannot removeOne non-existent engine for engineId: $engineId")))
    } { deadEngine =>
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      for {
        result <- deleteEngine(engineId).map(_ => Comment(s"Engine instance for engineId: $engineId deleted and all its data"))
        _ <- IO.effect(deadEngine.destroy()).mapError { e =>
          logger.error("Destroy engine error", e)
          ValidRequestExecutionError()
        }
      } yield result
    }
  }

  def systemInfo(): HIO[Response] = {
    logger.trace("Getting Harness system info")
    // todo: do we want to check connectons to services here?
    IO.effect(SystemInfo(
      buildVersion = com.actionml.admin.BuildInfo.version,
      gitBranch = Properties.envOrElse("BRANCH", "No git branch (BRANCH) detected in env." ),
      gitHash = Properties.envOrElse("GIT_HASH", "No git short commit number (GIT_HASH) detected in env." ),
      harnessURI = Properties.envOrElse("HARNESS_URI", "No HARNESS_URI set, using host and port" ),
      mongoURI = Properties.envOrElse("MONGO_URI", "ERROR: No URI set" ),
      elasticsearchURI = Properties.envOrElse("ELASTICSEARCH_URI", "No URI set,using host, port and protocol" )
    )).mapError { e =>
      logger.error("Get system info error", e)
      ValidRequestExecutionError("Get system info error")
    }
  }

  def statuses(): HIO[List[Response]] = {
    logger.trace("Getting status for all Engines")
    ZIO.collectAllPar(engines.map(_._2.status()).toList)
  }

  def status(resourceId: String): HIO[Response] = {
    if (engines.contains(resourceId)) {
      logger.trace(s"Getting status for $resourceId")
      engines(resourceId).status()
    } else {
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
  elasticsearchURI: String
) extends Response
