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
import com.actionml.core.model.GenericEngineParams
import com.actionml.core.storage.Mongo
import com.actionml.core.template.Engine
import com.actionml.core.validate._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject

class MongoAdministrator extends Administrator with JsonParser with Mongo {

  lazy val enginesCollection: MongoCollection = connection("harness_meta_store")("engines")
  lazy val commandsCollection: MongoCollection = connection("harness_meta_store")("commands") // async persistent though temporary commands
  var engines = Map.empty[EngineId, Engine]

  drawActionML
  private def newEngineInstance(engineFactory: String): Engine = {
    Class.forName(engineFactory).newInstance().asInstanceOf[Engine]
  }

  // instantiates all stored engine instances with restored state
  override def init() = {
    // ask engines to init
    engines = enginesCollection.find.map { engine =>
      val engineId = engine.get("engineId").toString
      val engineFactory = engine.get("engineFactory").toString
      val params = engine.get("params").toString
      // create each engine passing the params
      val e = (engineId -> newEngineInstance(engineFactory).initAndGet(params))
      if (e._2 == null) { // it is possible that previously valid metadata is now bad, the Engine code must have changed
        logger.error(s"Error creating engineId: $engineId from $params" +
          s"\n\nTrying to recover by deleting the previous Engine metadata but data may still exist for this Engine, which you must " +
          s"delete by hand from whatever DB the Engine uses then you can re-add a valid Engine JSON config and start over. Note:" +
          s"this only happens when code for one version of the Engine has chosen to not be backwards compatible.")
        // Todo: we need a way to cleanup in this situation
        enginesCollection.remove(MongoDBObject("engineId" -> engineId))
        // can't do this because the instance is null: deadEngine.destroy(), maybe we need a compan ion object with a cleanup function?
      }
      e
    }.filter(_._2 != null).toMap
    drawInfo("Harness Server Init", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Number of Engines: ", engines.size),
      ("Engines: ", engines.map(_._1))))

    this
  }

  def getEngine(engineId: EngineId): Option[Engine] = {
    engines.get(engineId)
  }

  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): Validated[ValidateError, EngineId] = {
    // val params = parse(json).extract[GenericEngineParams]
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      val newEngine = newEngineInstance(params.engineFactory).initAndGet(json)
      if (newEngine != null && enginesCollection.find(MongoDBObject("engineId" -> params.engineId)).size == 1) {
        // re-initialize
        logger.trace(s"Re-initializing engine for resource-id: ${ params.engineId } with new params $json")
        val query = MongoDBObject("engineId" -> params.engineId)
        val update = MongoDBObject("$set" -> MongoDBObject("engineFactory" -> params.engineFactory, "params" -> json))
        enginesCollection.findAndModify(query, update)
        Valid(params.engineId)
      } else if (newEngine != null) {
        //add new
        engines += params.engineId -> newEngine
        logger.trace(s"Initializing new engine for resource-id: ${ params.engineId } with params $json")
        val builder = MongoDBObject.newBuilder
        builder += "engineId" -> params.engineId
        builder += "engineFactory" -> params.engineFactory
        builder += "params" -> json
        enginesCollection += builder.result()
        Valid(params.engineId)

      } else {
        // ignores case of too many engine with the same engineId
        Invalid(ParseError(s"Unable to create Engine: ${params.engineId}, the config JSON seems to be in error"))
      }
    }
  }

  override def updateEngine(json: String): Validated[ValidateError, String] = {
    parseAndValidate[GenericEngineParams](json).andThen { params =>
      engines.get(params.engineId).map { existingEngine =>
        logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
        val query = MongoDBObject("engineId" -> params.engineId)
        val update = MongoDBObject("$set" -> MongoDBObject("engineFactory" -> params.engineFactory, "params" -> json))
        enginesCollection.findAndModify(query, update)
        existingEngine.init(json, deepInit = false).andThen(_ => Valid(
          """{
            |  "comment":"Get engine status to see what was changed."
            |}
          """.stripMargin))
      }.getOrElse(Invalid(WrongParams(s"Unable to update Engine: ${params.engineId}, the engine does not exist")))
    }
  }

  override def updateEngineWithImport(engineId: String, importPath: String): Validated[ValidateError, String] = {
    engines.get(engineId).map { existingEngine =>
      logger.trace(s"Importing a batch of events into engine: ${engineId} from $importPath")
      existingEngine.batchInput(importPath).andThen(_ => Valid(
        """{
          |  "comment":"New events imported"
          |}
        """.stripMargin))
    }.getOrElse(Invalid(WrongParams(s"Unable to import to Engine: $engineId}, the engine does not exist")))
  }

  override def removeEngine(engineId: String): Validated[ValidateError, Boolean] = {
    if (engines.contains(engineId)) {
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      val deadEngine = engines(engineId)
      engines = engines - engineId
      enginesCollection.remove(MongoDBObject("engineId" -> engineId))
      deadEngine.destroy()
      Valid(true)
    } else {
      logger.warn(s"Cannot remove non-existent engine for id: $engineId")
      Invalid(WrongParams(s"Cannot remove non-existent engine for id: $engineId"))
    }
  }

  override def status(resourceId: Option[String] = None): Validated[ValidateError, String] = {
    if (resourceId.nonEmpty) {
      if (engines.contains(resourceId.get)) {
        logger.trace(s"Getting status for ${resourceId.get}")
        Valid(engines(resourceId.get).status().toString)
      } else {
        logger.error(s"Non-existent engine-id: ${resourceId.get}")
        Invalid(WrongParams(s"Non-existent engine-id: ${resourceId.get}"))
      }
    } else {
      logger.trace("Getting status for all Engines")
      Valid(engines.mapValues(_.status()).toSeq.mkString("\n"))
    }
  }

}

