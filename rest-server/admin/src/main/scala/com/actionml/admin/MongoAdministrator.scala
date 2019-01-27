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
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.store.DaoQuery
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.validate._


class MongoAdministrator extends Administrator with JsonSupport {
  import DaoQuery.syntax._
  private val storage = MongoStorage.getStorage("harness_meta_store", codecs = MongoStorageHelper.codecs)

  private lazy val enginesCollection = storage.createDao[EngineMetadata]("engines")
  @volatile private var engines = Map.empty[String, Engine]

  drawActionML
  private def newEngineInstance(engineFactory: String, json: String): Engine = {
    Class.forName(engineFactory).getMethod("apply", classOf[String]).invoke(null, json).asInstanceOf[Engine]
  }

  // instantiates all stored engine instances with restored state
  override def init() = {
    // ask engines to init
    engines = enginesCollection.findMany().map { engine =>
      // create each engine passing the params
      val e = engine.engineId -> newEngineInstance(engine.engineFactory, engine.params)
      if (e._2 == null) { // it is possible that previously valid metadata is now bad, the Engine code must have changed
        logger.error(s"Error creating engineId: ${engine.engineId} from ${engine.params}" +
          s"\n\nTrying to recover by deleting the previous Engine metadata but data may still exist for this Engine, which you must " +
          s"delete by hand from whatever DB the Engine uses then you can re-add a valid Engine JSON config and start over. Note:" +
          s"this only happens when code for one version of the Engine has chosen to not be backwards compatible.")
        // Todo: we need a way to cleanup in this situation
        enginesCollection.removeOne("engineId" === engine.engineId)
        // can't do this because the instance is null: deadEngine.destroy(), maybe we need a companion object with a cleanup function?
      }
      e
    }.filter(_._2 != null).toMap
    drawInfo("Harness Server Init", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Number of Engines: ", engines.size),
      ("Engines: ", engines.map(_._1))))

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
    // val params = parse(json).extract[GenericEngineParams]
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      val newEngine = newEngineInstance(params.engineFactory, json)
      if (newEngine != null && enginesCollection.findMany(DaoQuery(filter = Seq("engineId" === params.engineId))).size == 1) {
        // re-initialize
        logger.trace(s"Re-initializing engine for resource-id: ${ params.engineId } with new params $json")
        enginesCollection.saveOne(EngineMetadata(params.engineId, params.engineFactory, json))
        engines += params.engineId -> newEngine
        Valid(Comment(params.engineId))
      } else if (newEngine != null) {
        //add new
        logger.debug(s"Initializing new engine for resource-id: ${ params.engineId } with params $json")
        enginesCollection.saveOne(EngineMetadata(params.engineId, params.engineFactory, json))
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
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      engines.get(params.engineId).map { existingEngine =>
        logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
        enginesCollection.saveOne(EngineMetadata(params.engineId, params.engineFactory, json))
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
      logger.warn(s"Cannot removeOne non-existent engine for engineId: $engineId")
      Invalid(WrongParams(jsonComment(s"Cannot removeOne non-existent engine for engineId: $engineId")))
    }
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

}

case class EnginesStatuses(statuses: List[Response]) extends Response

case class EngineMetadata(
  engineId: String,
  engineFactory: String,
  params: String)
