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

import java.util.Date

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.engine.Dataset
import com.actionml.core.model.{Comment, Response}
import com.actionml.core.store.{DaoQuery, Store}
import com.actionml.core.validate._
import com.actionml.engines.ur.URAlgorithm.URAlgorithmParams
import com.actionml.engines.ur.UREngine.{UREvent, URItemProperties}
import org.json4s.JsonAST._
import org.json4s.{JArray, JObject}

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
  private val eventsDao = store.createDao[UREvent](getEventsCollectionName)
  private val itemsDao = store.createDao[URItemProperties](getItemsCollectionName)
  def getItemsDao = itemsDao
  def getIndicatorsDao = eventsDao

  private def getItemsCollectionName = "items"
  def getEventsCollectionName = "events"

  // Engine Params from the JSON config plus defaults
  private var params: URAlgorithmParams = _

  private var indicatorNames: Seq[String] = _

  override def init(jsonConfig: String, update: Boolean = false): Validated[ValidateError, Response] = {
    parseAndValidate[URAlgorithmParams](
      jsonConfig,
      errorMsg = s"Error in the Algorithm part of the JSON config for engineId: $engineId, which is: " +
        s"$jsonConfig",
      transform = _ \ "algorithm").andThen { p =>
      params = p

      indicatorNames = params.indicators.map(_.name)

      Valid(p)
    }
    Valid(Comment("URDataset initialized"))
  }

  /** Cleanup all persistent data or processes created by the Dataset */
  override def destroy(): Unit = {
    // todo: Yikes this cannot be used with the sharedDb or all data from all engines will be dropped!!!!!
    // must drop only the data from collections
    store.drop //.dropDatabase(engineId)
  }

  // Parse, validate, drill into the different derivative event types, andThen(persist)?
  override def input(jsonEvent: String): Validated[ValidateError, UREvent] = {
    import DaoQuery.syntax._
    parseAndValidate[JObject](jsonEvent, errorMsg = s"Invalid UREvent JSON: $jsonEvent").andThen(toUrEvent).andThen { event =>
      val aliases = params.indicators.flatMap { ip =>
        ip.aliases.getOrElse(Seq(ip.name))
      } // should be either aliases for an event name, defaulting to the event name itself

      if(aliases.contains(event.event)) { // only store the indicator events here
        eventsDao.saveOne(event)
        Valid(event)
      } else { // not an indicator so check for reserved events the dataset cares about
        event.event match {
          case "$delete" =>
            event.entityType match {
              case "user" =>
                eventsDao.removeMany("entityId" === event.entityId)
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
            event.entityType match {
              case "user" =>
                logger.info(s"User properties not supported, send as named indicator event.")
                Invalid(NotImplemented(jsonComment(s"User properties not supported, send as named indicator event.")))
              case "item" =>
                val updateItem = itemsDao.findOneById(event.entityId).getOrElse(URItemProperties(event.entityId, Map.empty))
                itemsDao.saveOneById(
                  event.entityId,
                  URItemProperties(
                    _id = updateItem._id,
                    dateProps = updateItem.dateProps ++ event.dateProps,
                    categoricalProps = updateItem.categoricalProps ++ event.categoricalProps,
                    floatProps = updateItem.floatProps ++ event.floatProps,
                    booleanProps = updateItem.booleanProps ++ event.booleanProps
                  )
                )
              case _ =>
                logger.error(s"Unknown entityType: ${event.entityType} for $$delete")
                Invalid(NotImplemented(jsonComment(s"Unknown entityType: ${event.entityType} for $$delete")))
            }
          case _ =>
            logger.warn(s"Unknown event, not a reserved event, not an indicator. Ignoring. \n${prettify(jsonEvent)}")

        }

        Valid(event)
      }
    }
  }


  private val emptyProps = (Map.empty[String, Date], Map.empty[String, Seq[String]], Map.empty[String, Float], Map.empty[String, Boolean])
  private def parseProps(j: JObject): (Map[String, Date], Map[String, Seq[String]], Map[String, Float], Map[String, Boolean]) = {
    j.foldField(emptyProps) {
      case ((d, s, f, b), field) => field._2 match {
        case v@JString(_) => (d + (field._1 -> v.as[Date]), s, f, b)
        case JArray(v) => (d, s + (field._1 -> v.map(_.as[String])), f, b)
        case JDecimal(v) => (d, s, f + (field._1 -> v.toFloat), b)
        case JDouble(v) => (d, s, f + (field._1 -> v.toFloat), b)
        case JInt(v) => (d, s, f + (field._1 -> v.toFloat), b)
        case JLong(v) => (d, s, f + (field._1 -> v.toFloat), b)
        case JBool(v) => (d, s, f, b + (field._1 -> v))
        case _ => (d, s, f, b)
      }
    }
  }

  private def toUrEvent(j: JObject): Validated[ValidateError, UREvent] = {
    try {
      val eventId = (j \ "eventId").getAs[String]
      val event = (j \ "event").as[String]
      val entityType = (j \ "entityType").as[String]
      val entityId = (j \ "entityId").as[String]
      val targetEntityId = (j \ "targetEntityId").getAs[String]
      val (dateProps, categoricalProps, floatProps, booleanProps) = (j \ "properties").getAs[JObject]
        .fold(emptyProps)(parseProps)
      val conversionId = (j \ "conversionId").getAs[String]
      val eventTime = (j \ "eventTime").as[Date]
      Valid(UREvent(eventId, event, entityType, entityId, targetEntityId, dateProps, categoricalProps, floatProps, booleanProps, eventTime))
    } catch {
      case e: Exception =>
        logger.error(s"Can't parse UREvent from $j", e)
        Invalid(ParseError(s"Can't parse $j as UREvent"))
    }
  }
}
