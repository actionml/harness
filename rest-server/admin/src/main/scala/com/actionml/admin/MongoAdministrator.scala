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

package com.actionml.admin

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{ContextShift, IO, Timer}
import com.actionml.core._
import com.actionml.core.engine.Engine
import scalacache._
import scalacache.guava._
import cats.effect.IO
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.store.DaoQuery
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.validate._
import io.chrisdavenport.circuit.CircuitBreaker

import scala.concurrent.duration._
import scala.util.{Properties, Try}

class MongoAdministrator extends Administrator with JsonSupport {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = MongoStorageHelper.codecs)

  private lazy val enginesCollection = storage.createDao[EngineMetadata]("engines")

  private val enginesCache: Cache[List[Engine]] = GuavaCache[List[Engine]]

  private val engines: IO[List[Engine]] = {
    implicit val mode: Mode[IO] = scalacache.CatsEffect.modes.async
    val enginesCB = {
      import cats.implicits._
      val ec = scala.concurrent.ExecutionContext.Implicits.global
      implicit val timer: Timer[IO] = IO.timer(ec)
      implicit val cs: ContextShift[IO] = IO.contextShift(ec)
      CircuitBreaker.of[IO](
        maxFailures = 3,
        resetTimeout = 10.seconds
      ).flatMap(_.protect {
        enginesCollection.findManyIO()
          .flatMap(_.map(e => newEngineInstance(e.engineFactory, e.params)).toList.sequence)
      })
    }
    enginesCache.cachingForMemoizeF("engines")(Some(5.seconds))(enginesCB)
  }


  drawActionML
  private def newEngineInstance(engineFactory: String, json: String): IO[Engine] = {
    IO.fromTry(Try(
      Class.forName(engineFactory)
        .getMethod("apply", classOf[String], classOf[Boolean])
        .invoke(null, json, java.lang.Boolean.TRUE)
        .asInstanceOf[Engine]
    ))
  }

  // instantiates all stored engine instances with restored state
  override def init() = this

  def getEngine(engineId: String): Option[Engine] = {
    getEngines.get(engineId)
  }
  private def getEngines: Map[String, Engine] = {
    engines.unsafeRunSync().map(e => e.engineId -> e).toMap
  }

  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): IO[Validated[ValidateError, _ <: Response]] = {
    import DaoQuery.syntax._
    val ec = scala.concurrent.ExecutionContext.Implicits.global
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    (for {
      params <- parseAndValidateIO[GenericEngineParams](json)
      noOfEngines <- enginesCollection.findManyIO(DaoQuery(filter = Seq("engineId" === params.engineId)))
      newEngine <- noOfEngines.size match {
        case 0 =>
          newEngineInstance(params.engineFactory, json).map { engine =>
            enginesCollection.insert(EngineMetadata(params.engineId, params.engineFactory, json))
            engine
          }
        case 1 =>
          newEngineInstance(params.engineFactory, json).map { engine =>
            enginesCollection.saveOne("engineId" === params.engineId , EngineMetadata(params.engineId, params.engineFactory, json))
            engine
          }
        case _ =>
          IO.raiseError(new RuntimeException(s"Too many engine(s) with this id ${params.engineId}"))
      }
    } yield Valid(Comment(newEngine.engineId))).handleErrorWith { e =>
      val errMsg = "Unable to create Engine the config JSON seems to be in error"
      logger.error(errMsg, e)
      IO(Invalid(ParseError(jsonComment(errMsg))))
    }
  }

  override def updateEngine(json: String): Validated[ValidateError, Response] = {
    import DaoQuery.syntax._
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      getEngine(params.engineId).map { existingEngine =>
        logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
        enginesCollection.saveOne("engineId" === existingEngine.engineId, EngineMetadata(params.engineId, params.engineFactory, json))
        existingEngine.init(json, update = true)
      }.getOrElse(Invalid(WrongParams(jsonComment(s"Unable to update Engine: ${params.engineId}, the engine does not exist"))))
    }
  }

  override def updateEngineWithImport(engineId: String, importPath: String): Validated[ValidateError, Response] = {
    getEngine(engineId).map(_.batchInput(importPath))
      .getOrElse(Invalid(ResourceNotFound(jsonComment(s"No Engine instance found for engineId: $engineId"))))
  }

  override def updateEngineWithTrain(engineId: String): Validated[ValidateError, Response] = {
    getEngine(engineId).map(_.train())
      .getOrElse(Invalid(WrongParams(jsonComment(s"Unable to train Engine: $engineId, the engine does not exist"))))
  }

  override def removeEngine(engineId: String): Validated[ValidateError, Response] = {
    getEngine(engineId).map { deadEngine =>
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      enginesCollection.removeOne("engineId" === engineId)
      deadEngine.destroy()
      Valid(Comment(s"Engine instance for engineId: $engineId deleted and all its data"))
    }.getOrElse {
      logger.warn(s"Cannot removeOne, non-existent engine for engineId: $engineId")
      Invalid(WrongParams(jsonComment(s"Cannot removeOne non-existent engine for engineId: $engineId")))
    }
  }

  override def systemInfo(): Validated[ValidateError, Response] = {
    logger.trace("Getting Harness system info")
    // todo: do we want to check connectons to services here?
    Valid(SystemInfo(
      buildVersion = com.actionml.admin.BuildInfo.version,
      gitBranch = Properties.envOrElse("BRANCH", "No git branch (BRANCH) detected in env." ),
      gitHash = Properties.envOrElse("GIT_HASH", "No git short commit number (GIT_HASH) detected in env." ),
      harnessURI = Properties.envOrElse("HARNESS_URI", "No HARNESS_URI set, using host and port" ),
      mongoURI = Properties.envOrElse("MONGO_URI", "ERROR: No URI set" ),
      elasticsearchURI = Properties.envOrElse("ELASTICSEARCH_URI", "No URI set,using host, port and protocol" )
    ))
  }

  override def statuses(): Validated[ValidateError, List[Response]] = {
    logger.trace("Getting status for all Engines")
    val empty = List.empty
    getEngines.map(_._2.status()).foldLeft[Validated[ValidateError, List[Response]]](Valid(empty)) { (acc, s) =>
      acc.andThen(statuses => s.map(status => statuses :+ status))
    }
  }

  override def status(resourceId: String): Validated[ValidateError, Response] = {
    getEngine(resourceId).map { engine =>
      logger.trace(s"Getting status for $resourceId")
      engine.status()
    }.getOrElse {
      logger.error(s"Non-existent engine-id: $resourceId")
      Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $resourceId")))
    }
  }

  override def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    getEngine(engineId).map { engine =>
      engine.cancelJob(engineId, jobId)
    }.getOrElse {
      logger.error(s"Non-existent engine-id: $engineId")
      Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $engineId")))
    }
  }
}

case class EnginesStatuses(statuses: List[Response]) extends Response

case class EngineMetadata(
  engineId: String,
  engineFactory: String,
  params: String)

case class SystemInfo(
    buildVersion: String,
    gitBranch: String,
    gitHash: String,
    harnessURI: String,
    mongoURI: String,
    elasticsearchURI: String
)
  extends Response
