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
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.BadParamsException
import com.actionml.core.engine.Dataset
import com.actionml.core.store.{DaoQuery, Store}
import com.actionml.core.validate._
import com.actionml.engines.ur.URAlgorithm.{DefaultURAlgoParams, URAlgorithmParams}
import com.actionml.engines.ur.UREngine.{ItemProperties, UREvent}

import scala.language.reflectiveCalls

/** Scaffold for a Dataset, does nothing but is a good starting point for creating a new Engine
  * Extend with the store of choice, like Mongo or other Store trait.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  *
  * @param engineId The Engine ID
  */
class URDataset(engineId: String, val store: Store) extends Dataset[UREvent](engineId) with JsonSupport {

  // todo: make sure to index the timestamp for descending ordering, and the name field for filtering
  private val eventsDao = store.createDao[UREvent](getIndicatorEventsCollectionName)
  private val itemsDao = store.createDao[ItemProperties](esType)
  def getItemsDao = itemsDao
  def getIndicatorsDao = eventsDao


  // This holds a place for any properties that should go into the model at training time
  private val esIndex = store.dbName // index and db name should be the same
  private val esType = DefaultURAlgoParams.ModelType
  private def getItemsDbName = esIndex
  private def getItemsCollectionName = esType
  def getIndicatorEventsCollectionName = "events"

  // Engine Params from the JSON config plus defaults
  private var params: URAlgorithmParams = _

  private var indicatorNames: Seq[String] = _

  override def init(jsonConfig: String, update: Boolean = false): Validated[ValidateError, String] = {
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
    // todo: Yikes this cannot be used with the sharedDb or all data from all engines will be dropped!!!!!
    // must drop only the data from collections
    store.drop //.dropDatabase(engineId)
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(jsonEvent: String): Validated[ValidateError, UREvent] = {
    parseAndValidate[UREvent](jsonEvent, errorMsg = s"Invalid UREvent JSON: $jsonEvent").andThen { event =>
      if (indicatorNames.contains(event.event)) { // only store the indicator events here
        eventsDao.saveOne(event)
        Valid(event)
      } else { // not an indicator so check for reserved events the dataset cares about
        event.event match {
          case "$delete" =>
            event.entityType match {
              case "user" =>
                eventsDao.removeMany(("entityId", event.entityId))
                logger.info(s"Deleted data for user: ${event.entityId}, retrain to get it reflected in new queries")
                Valid(jsonComment(s"deleted data for user: ${event.entityId}"))
              case "item" =>
                itemsDao.removeOneById(event.entityId)
                logger.info(s"Deleted properties for item: ${event.entityId}")
              case _ =>
                logger.error(s"Unknown entityType: ${event.entityType} for $$delete")
                Invalid(NotImplemented(jsonComment(s"Unknown entityType: ${event.entityType} for $$delete")))
            }
          case "$set" => // only item properties as allowed here and used for business rules once they are reflected in
            // the model, which should be immediately but done by the URAlgorithm, which manages the model

        }

        Valid(event)
      }
    }
  }

}

