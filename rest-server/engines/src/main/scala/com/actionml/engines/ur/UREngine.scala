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

package com.actionml.engines.ur

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.drawInfo
import com.actionml.core.engine.Engine
import com.actionml.core.model.{EngineParams, Event, GenericEvent, GenericQuery}
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.validate.ValidateError
import com.actionml.engines.cb.MongoStorageHelper
import com.actionml.engines.ur.UREngine.{UREngineParams, UREvent}
import org.json4s.JValue

class UREngine extends Engine {

  private var dataset: URDataset = _
  private var algo: URAlgorithm = _
  private var params: UREngineParams = _

  /** Initializing the Engine sets up all needed objects */
  override def init(jsonConfig: String, deepInit: Boolean = true): Validated[ValidateError, Boolean] = {
    parseAndValidate[UREngineParams](jsonConfig).andThen { p =>
      params = p
      engineId = params.engineId
      val dbName = p.sharedDBName.getOrElse(engineId)
      dataset = new URDataset(engineId = engineId, store = MongoStorage.getStorage(dbName, MongoStorageHelper.codecs))
      algo = URAlgorithm(this, jsonConfig, dataset)
      drawInfo("Generic UR Engine", Seq(
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("EngineId: ", engineId)))

      Valid(p)
    }.andThen { p =>
      dataset.init(jsonConfig).andThen { r =>
        algo.init(this)
      }
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  // Todo: should return null for bad init
  override def initAndGet(jsonConfig: String): UREngine = {
    val response =init(jsonConfig)
    if (response.isValid) {
      logger.trace(s"Initialized with JSON: $jsonConfig")
      this
    } else {
      logger.error(s"Parse error with JSON: $jsonConfig")
      null.asInstanceOf[UREngine] // todo: ugly, replace
    }
  }

  // todo: should merge base engine status with UREngine's status
  override def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of UREngine with engineId:$engineId")
    Valid(this.params.toString)
  }

  // todo: should kill any pending Spark jobs
  override def destroy(): Unit = {
    logger.info(s"Dropping persisted data for id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  /** Triggers parse, validation, and persistence of event encoded in the jsonEvent */
  override def input(jsonEvent: String): Validated[ValidateError, Boolean] = {
    logger.trace("Got JSON body: " + jsonEvent)
    // validation happens as the input goes to the dataset
    super.input(jsonEvent).andThen(_ => dataset.input(jsonEvent)).andThen { _ =>
      parseAndValidate[UREvent](jsonEvent).andThen(algo.input)
    }
    //super.input(jsonEvent).andThen(dataset.input(jsonEvent)).andThen(algo.input(jsonEvent)).map(_ => true)
  }

  override def train(): Validated[ValidateError, String] = {
    logger.info("got to UR.train")
    algo.train()
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(jsonQuery: String): Validated[ValidateError, String] = {
    logger.trace(s"Got a query JSON string: $jsonQuery")
    parseAndValidate[GenericQuery](jsonQuery).andThen { query =>
      // query ok if training group exists or group params are in the dataset
      val result = algo.query(query)
      Valid(result.toJson)
    }
  }

}

object UREngine {
  def apply(jsonConfig: String): UREngine = {
    val engine = new UREngine()
    engine.initAndGet(jsonConfig)
  }

  case class UREngineParams(
      engineId: String, // required, resourceId for engine
      engineFactory: String,
      mirrorType: Option[String] = None,
      mirrorContainer: Option[String] = None,
      sharedDBName: Option[String] = None,
      sparkConf: Map[String, JValue])
    extends EngineParams

  case class UREvent (
      //eventId: String, // not used in Harness, but allowed for PIO compatibility
      event: String,
      entityType: String,
      entityId: String,
      targetEntityId: Option[String] = None,
      properties: Option[Map[String, Any]] = None,
      eventTime: String) // ISO8601 date
    extends Event

  case class ItemProperties (
      _id: String, // must be the same as the targetEntityId for the $set event that changes properties in the model
      properties: Map[String, Any] // properties to be written to the model, this is saved in the input dataset
  )

}

