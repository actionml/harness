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
import com.actionml.core.model.{Event, GenericEngineParams, GenericEvent}
import com.actionml.core.engine.Dataset
import com.actionml.core.store.Store
import com.actionml.core.validate._
import com.actionml.engines.navhinting.Journey
import com.actionml.engines.ur.URAlgorithm.{DefaultIndicatorParams, DefaultURAlgoParams, URAlgorithmParams}
import com.actionml.engines.ur.UREngine.{ItemProperties, UREngineParams, UREvent}
import org.apache.zookeeper.KeeperException.BadArgumentsException

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
  private var activeJourneysDAO = store.createDao[UREvent]("indicator_events")

  // This holds a place for any properties that should go into the model at training time
  private val esIndex = store.dbName // index and db name should be the same
  private val esType = DefaultURAlgoParams.ModelType
  private val itemsDAO = store.createDao[ItemProperties](esType)
  def getItemsDbName = esIndex
  def getItemsCollectionName = esType

  private var params: URAlgorithmParams = _

  // we assume the list of event names is in the params if not the config is rejected by some earlier stage since
  // this is not calculated until an engine is created with the config and taking input
  private var indicatorNames: Seq[String] = _

  // These should only be called from trusted source like the CLI!
  override def init(jsonConfig: String, deepInit: Boolean = true): Validated[ValidateError, Boolean] = {
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
    Valid(true)
  }

  /** Cleanup all persistent data or processes created by the Dataset */
  override def destroy(): Unit = {
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(jsonEvent: String): Validated[ValidateError, UREvent] = {
    parseAndValidate[UREvent](jsonEvent, errorMsg = s"Invalid UREvent JSON: $jsonEvent").andThen { event =>
      if (indicatorNames.contains(event.event)) { // only store the indicator events here
        // todo: make sure to index the timestamp for descending ordering, and the name field for filtering
        activeJourneysDAO.save(event)

        Valid(event)
      } else {
        event.event match {
          case "$set" =>
            // change properties of the targetEntity, parse them out first
            // todo: get the item from the model, set the new values of fields in the document. this allows
            // real-time changes to the model
            // todo: do the same for every item in the dataset so that train will not change the model
            // this allows real-time changes to the model and batch training will not wipe out the changes

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

