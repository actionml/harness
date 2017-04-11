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

/** REST admin type commands, these are independent of Template

  * PUT /datasets/<dataset-id>
  * Action: returns 404 since writing an entire dataset is not supported

  * POST /datasets/
  * Request Body: JSON for PIO dataset description describing Dataset
  * created, must include in the JSON `"resource-id": "<some-string>"
      the resource-id is returned. If there is no `resource-id` one will be generated and returned
    Action: sets up a new empty dataset with the id specified.

DELETE /datasets/<dataset-id>
    Action: deletes the dataset including the dataset-id/empty dataset
      and removes all data

POST /engines/<engine-id>
    Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
      Success/failure indicated in the HTTP return code
    Action: creates or modifies an existing engine

DELETE /engines/<engine-id>
    Action: removes the specified engine but none of the associated
      resources like dataset but may delete model(s). Todo: this last
      should be avoided but to delete models separately requires a new
      resource type.

GET /commands/list?engines
GET /commands/list?datasets
GET /commands/list?commands

  */
package com.actionml.router.admin

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.admin.Administrator
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Engine, EngineParams}
import com.actionml.core.validate._
import com.actionml.templates.cb.CBEngine
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import org.json4s.MappingException
import org.json4s.jackson.JsonMethods._

class MongoAdministrator() extends Administrator() with JsonParser {

  lazy val store = new Mongo(m = config.getString("mongo.host"), p = config.getInt("mongo.port"), n = "metaStore")

//  val engines = store.client.getDB("metaStore").getCollection("engines")
//  val datasets = store.client.getDB("metaStore").getCollection("datasets")
//  val commands = store.client.getDB("metaStore").getCollection("commands") // async persistent though temporary commands
  lazy val enginesCollection = store.connection("meta_store")("engines")
  //lazy val datasetsCollection = store.connection("meta_store")("datasets")
  lazy val commandsCollection = store.connection("meta_store")("commands") // async persistent though temporary commands
  var engines = Map.empty[String, Engine]


  // instantiates all stored engine instances with restored state
  override def init() = {
    // ask engines to init
    engines = enginesCollection.find.map { engine =>
      val engineId = engine.get("engineId").toString
      val engineFactory = engine.get("engineFactory")
      val params = engine.get("params").toString
      // create each engine passing the params
      // Todo: Semen, this happens on startup where all exisitng Engines will be started it replaces some of the code
      // in Main See CmdLineDriver for what should be done to integrate.
      engineId -> (new CBEngine).initAndGet(params)
    }.filter(_._2 != null).toMap
    this
  }

  def getEngine(engineId: String): Engine = {
    engines(engineId)
  }

  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(json: String): Validated[ValidateError, Boolean] = {
    // val params = parse(json).extract[RequiredEngineParams]
    parseAndValidate[RequiredEngineParams](json).andThen { params =>
      engines = engines + (params.engineId -> new CBEngine().initAndGet(json))
      if (engines(params.engineId) != null) {
        if(enginesCollection.find(MongoDBObject("EngineId" -> params.engineId)).size == 1) {
          // re-initialize
          logger.trace(s"Re-initializing engine for resource-id: ${params.engineId} with new params $json")
          enginesCollection.findAndModify(MongoDBObject("engineId" -> params.engineId),
            MongoDBObject("engineFactory" -> params.engineFactory, "params" -> json))
        } else {
          //add new
          logger.trace(s"Initializing new engine for resource-id: ${params.engineId} with params $json")
          val builder = MongoDBObject.newBuilder
          builder += "engineId" -> params.engineId
          builder += "engineFactory" -> params.engineFactory
          builder += "params" -> json
          enginesCollection += builder.result()

        } // ignores case of too many engine with the same engineId
        Valid(true)
      } else {
        // init failed
        engines = engines - params.engineId //remove bad engine
        logger.error(s"Failed to re-initializing engine for resource-id: ${params.engineId} with new params $json")
        Invalid(ParseError(s"Failed to re-initializing engine for resource-id: ${params.engineId} with new params $json"))
      }
    }
  }

  def removeEngine(engineId: String): Validated[ValidateError, Boolean] = {
    if (engines.keySet.contains(engineId)) {
      logger.info(s"Stopped and removed engine and all data for id: $engineId")
      val deadEngine = engines(engineId)
      engines = engines - engineId
      enginesCollection.findAndRemove(MongoDBObject("engineId" -> engineId))
      deadEngine.destroy()
      Valid(true)
    } else {
      logger.warn(s"Cannot remove non-existent engine for id: $engineId")
      Invalid(WrongParams(s"Cannot remove non-existent engine for id: $engineId"))
    }
  }

  def list(resourceType: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

}

case class RequiredEngineParams(
  engineId: String, // required, resourceId for engine
  engineFactory: String
) extends EngineParams
