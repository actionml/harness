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

PUT /datasets/<dataset-id>
    Action: returns 404 since writing an entire dataset is not supported

POST /datasets/
    Request Body: JSON for PIO dataset description describing Dataset
      created, must include in the JSON `"resource-id": "<some-string>"
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
package com.actionml.core.admin

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.storage.Mongo
import com.actionml.core.template.EngineParams
import com.actionml.core.validate.ValidateError
import com.typesafe.config.ConfigFactory

class MongoAdministrator() extends Administrator() {

  private lazy val config = ConfigFactory.load()
  lazy val store = new Mongo(m = config.getString("mongo.host"), p = config.getInt("mongo.port"), n = "metaStore")

  val engines = store.client.getDB("metaStore").getCollection("engines")
  val datasets = store.client.getDB("metaStore").getCollection("datasets")
  val commands = store.client.getDB("metaStore").getCollection("commands") // async persistent though temporary commands


  /*
  POST /engines/<engine-id>
  Request Body: JSON for engine configuration engine.json file
    Response Body: description of engine-instance created.
  Success/failure indicated in the HTTP return code
  Action: creates or modifies an existing engine
  */
  def addEngine(engineId: String, engineJson: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

  def addDataset(datasetId: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

  def removeEngine(engineId: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

  def removeDataset(datasetId: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

  def list(resourceType: String): Validated[ValidateError, Boolean] = {
    Valid(true)
  }

  def parseAndValidateInput(json: String): Validated[ValidateError, EngineParams] = {
    Valid(RawEngineParams)
  }

}

case class RawEngineParams(
  engineId: String, // required, resourceId for engine
  engineFactory:
  datasetId: String, // required, eventual resource-id for dataset, may not be created yet
)
