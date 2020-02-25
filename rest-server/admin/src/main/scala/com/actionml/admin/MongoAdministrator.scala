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
import com.actionml.core._
import com.actionml.core.engine.Engine
import com.actionml.core.jobs.JobManager
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.store.{DAO, DaoQuery}
import com.actionml.core.validate._
import zio.clock.Clock
import zio.duration._
import zio.stream.ZStream
import zio.{DefaultRuntime, Schedule, ZIO}

import scala.util.{Properties, Try}


class MongoAdministrator extends Administrator with JsonSupport {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = MongoStorageHelper.codecs)

  private lazy val enginesCollection = storage.createDao[EngineMetadata]("engines")
  @volatile private var engines = Map.empty[String, Engine]

  new DefaultRuntime{}.unsafeRunAsync(mkEnginesStream(enginesCollection).foreach { enginesList =>
    if (this.engines.keySet.toList != enginesList.map(_.engineId)) this.engines = enginesList.map(e => e.engineId -> e).toMap
    ZIO.unit
  })(_ => ())

  def mkEnginesStream(enginesDao: DAO[EngineMetadata]): ZStream[Clock, Nothing, List[Engine]] = {
    def toEngines(docs: Iterable[EngineMetadata]): ZIO[Clock, Nothing, List[Engine]] = {
      ZIO.fromFunction(_ => docs.toList.flatMap { meta =>
        Try(newEngineInstance(meta.engineFactory, meta.params)).toOption.flatMap(Option.apply).fold[Option[Engine]] {
          logger.error(s"Error creating engineId: ${meta.engineId} from ${meta.params}" +
            s"\n\nTrying to recover by deleting the previous Engine metadata but data may still exist for this Engine, which you must " +
            s"delete by hand from whatever DB the Engine uses then you can re-add a valid Engine JSON config and start over. Note:" +
            s"this only happens when code for one version of the Engine has chosen to not be backwards compatible.")
          enginesCollection.removeOne("engineId" === meta.engineId)
          // can't do this because the instance is null: deadEngine.destroy(), maybe we need a companion object with a cleanup function?
          None
        } { a => Option(a)}
      })
    }
    ZStream.repeatEffectWith(toEngines(enginesDao.findMany()), Schedule.linear(30.seconds)) // this can be replaced with stream from kafka or redis or...
  }

  drawActionML
  private def newEngineInstance(engineFactory: String, json: String): Engine = {
    Class.forName(engineFactory).getMethod("apply", classOf[String], classOf[Boolean]).invoke(null, json, java.lang.Boolean.TRUE).asInstanceOf[Engine]
  }

  // instantiates all stored engine instances with restored state
  override def init() = {
    // ask engines to init
    JobManager.abortExecutingJobs
    drawInfo("Harness Server Init", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Number of Engines: ", engines.size),
      ("Engines: ", engines.keys)))
    this
  }

  def getEngine(engineId: String): Option[Engine] = {
    engines.get(engineId)
  }

  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): Validated[ValidateError, Response] = {
    import DaoQuery.syntax._
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      val newEngine = newEngineInstance(params.engineFactory, json)
      if (enginesCollection.findMany("engineId" === params.engineId).nonEmpty) {
        logger.warn(s"Ignored, engine for resource-id: ${params.engineId} already exists, use update")
        Invalid(WrongParams(s"Engine ${params.engineId} already exists, use update"))
      } else if (newEngine != null) {
        //add new
        logger.trace(s"Initializing new engine for resource-id: ${ params.engineId } with params $json")
        enginesCollection.insert(EngineMetadata(params.engineId, params.engineFactory, json))
        // todo: this will not allow 2 harness servers with the same Engines, do not manage in-memory copy of engines?
        engines += params.engineId -> newEngine
        logger.debug(s"Engine for resource-id: ${params.engineId} with params $json initialized successfully")
        Valid(Comment(s"EngineId: ${params.engineId} created"))
      } else {
        // ignores case of too many engine with the same engineId
        Invalid(ParseError(jsonComment(s"Unable to create Engine the config JSON seems to be in error")))
      }
    }
  }

  override def updateEngine(json: String): Validated[ValidateError, Response] = {
    import DaoQuery.syntax._
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      engines.get(params.engineId).map { existingEngine =>
        logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
        enginesCollection.saveOne("engineId" === existingEngine.engineId, EngineMetadata(params.engineId, params.engineFactory, json))
        existingEngine.init(json, update = true)
      }.getOrElse(Invalid(WrongParams(jsonComment(s"Unable to update Engine: ${params.engineId}, the engine does not exist"))))
    }
  }

  override def updateEngineWithImport(engineId: String, importPath: String): Validated[ValidateError, Response] = {
    if(engines.get(engineId).isDefined) {
      engines(engineId).batchInput(importPath)
    } else Invalid(ResourceNotFound(jsonComment(s"No Engine instance found for engineId: $engineId")))
  }

  override def updateEngineWithTrain(engineId: String): Validated[ValidateError, Response] = {
    val eid = engines.get(engineId)
    if (eid.isDefined) {
      eid.get.train()
    } else {
      Invalid(WrongParams(jsonComment(s"Unable to train Engine: $engineId, the engine does not exist")))
    }
  }

  override def removeEngine(engineId: String): Validated[ValidateError, Response] = {
    if (engines.contains(engineId)) {
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      val deadEngine = engines(engineId)
      engines = engines - engineId
      enginesCollection.removeOne("engineId" === engineId)
      deadEngine.destroy()
      Valid(Comment(s"Engine instance for engineId: $engineId deleted and all its data"))
    } else {
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
    engines.map(_._2.status()).foldLeft[Validated[ValidateError, List[Response]]](Valid(empty)) { (acc, s) =>
      acc.andThen(statuses => s.map(status => statuses :+ status))
    }
  }

  override def status(resourceId: String): Validated[ValidateError, Response] = {
    if (engines.contains(resourceId)) {
      logger.trace(s"Getting status for $resourceId")
      engines(resourceId).status()
    } else {
      logger.error(s"Non-existent engine-id: $resourceId")
      Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $resourceId")))
    }
  }

  override def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    engines.get(engineId).map { engine =>
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
