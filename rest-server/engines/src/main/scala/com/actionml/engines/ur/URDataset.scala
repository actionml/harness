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
import com.actionml.core.BadParamsException
import com.actionml.engines.ur.URAlgorithm.{DefaultURAlgoParams, URAlgorithmParams}
import com.actionml.core.engine.Dataset
import com.actionml.core.store.{DAO, Store}
import com.actionml.core.validate._

import com.actionml.engines.ur.UREngine.{ItemProperties, UREngineParams, UREvent}

import scala.language.reflectiveCalls

/** Scaffold for a Dataset, does nothing but is a good starting point for creating a new Engine
  * Extend with the store of choice, like Mongo or other Store trait.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  *
  * @param engineId The Engine ID
  */
class URDataset(engineId: String, store: Store) extends Dataset[UREngine.UREvent](engineId) with JsonParser {

  // todo: make sure to index the timestamp for descending ordering, and the name field for filtering
  private val indicatorsDao = store.createDao[UREvent]("indicator_events")

  // This holds a place for any properties that should go into the model at training time
  private val esIndex = store.dbName // index and db name should be the same
  private val esType = DefaultURAlgoParams.ModelType
  protected val itemsDao = store.createDao[ItemProperties](esType) // the _id can be the name, it should be unique and indexed
  def getItemsDbName = esIndex
  def getItemsCollectionName = esType
  def getItemsDao = itemsDao

  private var params: URAlgorithmParams = _

  // we assume the findMany of event names is in the params if not the config is rejected by some earlier stage since
  // this is not calculated until an engine is created with the config and taking input
  protected var indicatorNames: Seq[String] = _

  // These should only be called from trusted source like the CLI!
  override def init(jsonConfig: String, deepInit: Boolean = true): Validated[ValidateError, String] = {
    parseAndValidate[URAlgorithmParams](
      jsonConfig,
      errorMsg = s"Error in the Algorithm part of the JSON config for engineId: $engineId, which is: " +
        s"$jsonConfig",
      transform = _ \ "algorithm").andThen { p =>
      params = p

      indicatorNames = if(params.indicators.isEmpty) {
        if(params.eventNames.isEmpty) {
          // yikes both empty so error so bad we can't init!
          throw BadParamsException("No indicator or eventNames in the config JSON file")
        } else {
          params.eventNames.get
        }
      } else {
        params.indicators.get.map(_.name)
      }

      Valid(p)
    }
    Valid(jsonComment("URDataset initialized"))
  }

  /** Cleanup all persistent data or processes created by the Dataset */
  override def destroy(): Unit = {
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(jsonEvent: String): Validated[ValidateError, UREvent] = {
    parseAndValidate[UREvent](jsonEvent, errorMsg = s"Invalid UREvent JSON: $jsonEvent").andThen { event =>
      if (indicatorNames.contains(event.event)) { // only store the indicator events here
        // todo: make sure to index the timestamp for descending ordering, and the name field for filtering
        indicatorsDao.saveOne(event)

        Valid(event)
      } else {
        event.event match {
          case "$delete" =>
            if(event.entityType == "user") {
              // this will only delete a user's data
              itemsDao.removeOne(filter=("entityId", event.entityId)) // removeOne all events by a user
            } // ignore any other $delete, they will be caught by the Algorithm if at all
          case _ =>
        }

        Valid(event)
      }
    }
  }

  // This is not needed, deprecate from Engine API
  override def parseAndValidateInput(jsonEvent: String): Validated[ValidateError, UREvent] = {
    parseAndValidate[UREvent](jsonEvent)
  }
}

