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

package com.actionml.templates.cb

import com.actionml.core.storage.{Mongo, Store}
import com.actionml.core.template.{Event, Dataset}
import com.actionml.router.http.HTTPStatusCodes
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import com.mongodb.casbah.Imports.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import com.novus.salat.global._
import com.mongodb.casbah.commons.conversions.scala._

/** DAO for the Contextual Bandit input data
  * There are 2 types of input events for the CB 1) usage events and 2) property change events. The usage events
  * are stored as a potentially very large time ordered collection, the property change events translate
  * into changes to mutable DB objects and so are always up-to-date with the last change made. Another way to
  * look at this is that usage events accumulate until train creates an updateable model then them may be discarded
  * since the model acts as a watermark requiring no history to create predictions. The properties are attached to
  * objects and ony the most recent update is needed so although encoded as events they cause a property change in
  * real time to the object.
  *
  * See the discussion of Kappa Learning here: https://github.com/actionml/pio-kappa/blob/master/kappa-learning.md
  *
  * This dataset contains collections of users, usage events, and groups. Each get input from the datasets POST
  * endpoint and are parsed and validated but go to different collections, each with different properties. The
  * users and groups are mutable in Mongo, the events are kept as a stream that is truncated after the model is
  * updated so not unnecessary old data is stored.
  *
  * @param resourceId REST resource-id from POST /datasets/<resource-id> also ids the mongo table for all input
  * @param store where they are stored, assumes Mongo for the Contextual Bandit
  */
class CBDataset(resourceId: String, store: Mongo) extends Dataset[CBEvent](resourceId, store) {

  implicit val formats = DefaultFormats  ++ JodaTimeSerializers.all //needed for json4s parsing
  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  var events = Seq[CBEvent]() // Todo: data will go in a Store eventually

  // These should only be called from trusted source like the CLI!
  def create(name: String = resourceId): CBDataset = {
    store.create(name)
    this
  }

  def destroy(): CBDataset = {
    store.destroy(resourceId)
    this
  }

  // add one json, possibly an CBEvent, to the beginning of the dataset
  def input(json: String): Int = {
    val (event, status) = parseAndValidateInput(json)
    if (status == HTTPStatusCodes.ok) persist(event) else status
    // train()? // kappa train happens here unless using micro-batch method
  }

  def persist(event: CBEvent): Int = {
    event match {
      //case C=> // either group or user updates
      //  logger.info(s"Dataset: ${resourceId} got a special event: ${json.event}")
      //  json.event(0) == "$" // modify the object
      case event: CBUsageEvent => // usage data, kept as a stream
        logger.info(s"Dataset: ${resourceId} got a usage event: ${event.event}")
        // input to usageEvents collection
        // Todo: validate fields first
        val eventObj = MongoDBObject(
          "user-id" -> event.entityId,
          "event-name" -> event.event,
          "item" -> event.targetEntityId,
          "group" -> event.properties.testGroupId,
          "converted" -> event.properties.converted,
          "eventTime" -> new DateTime(event.eventTime)) //sort by this

        store.client.getDB(resourceId).getCollection("usage-events").insert(eventObj)

      case _ =>
        logger.info("Unrecognized event, this message should never be seen!")
    }
    HTTPStatusCodes.ok // Todo: try/catch exceptions and return 400 if persist error
  }

  def parseAndValidateInput(json: String): (CBEvent, Int) = {
    // todo: all parse and extract exceptions should be caught validation of values happens in calling function
    // should report but ignore the input. HTTPStatusCodes should be ok or badRequest for
    // malformed data
    val event = parse(json).extract[CBRawEvent]
    event.event(0).toString match {
      case "$" => // either group or user updates
        event.entityType match {
          case "user" => // got a user profile update event
            logger.info(s"Dataset: ${resourceId} got a user update event: ${event.event}")
            val e = parse(json).extract[CBUserUpdateEvent]
            if ( e.properties.isDefined ) (e, HTTPStatusCodes.ok) else (e, HTTPStatusCodes.badRequest)
          case "group" | "testGroup" => // got a group initialize event, uses either new or old name
            logger.info(s"Dataset: ${resourceId} got a group init event: ${event.event}")
            (parse(json).extract[CBGroupInitEvent], HTTPStatusCodes.ok)
        }
      case _ => // usage data, kept as a stream
        logger.info(s"Dataset: ${resourceId} got a usage event: ${event.event}")
        (parse(json).extract[CBUsageEvent], HTTPStatusCodes.ok)
    }
   }

}

//case class CBGroupInitProperties( p: Map[String, Seq[String]])

/* CBUser Comes in CBEvent partially parsed from the Json:
{
  "event" : "$set",
  "entityType" : "user"
  "entityId" : "amerritt",
  "properties" : {
    "gender": ["male"],
    "country" : ["Canada"],
    "otherContextFeatures": ["A", "B"]
  }
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
  "creationTime" : "2014-11-02T09:39:45.618-08:00", // ignored, only created by PIO
}
 */
case class CBUserUpdateEvent(
    entityId: String,
    properties: Option[Map[String, List[String]]],
    eventTime: String)
  extends CBEvent

/* CBUsageEvent string
Some values are ignored since the only "usage event for teh Contextual Bandit is a page-view.
{
  "event" : "page-view-conversion", // value ignored
  "entityType" : "user", // value ignored
  "entityId" : "amerritt",
  "targetEntityType" : "item", // value ignored
  "targetEntityId" : "item-1",
  "properties" : {
    "testGroupId" : "group-1",
    "converted" : true
  }
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
  "creationTime" : "2014-11-02T09:39:45.618-08:00", // ignored, only created by PIO
}
*/
case class CBUsageProperties(
  testGroupId: String,
  converted: Boolean)

case class CBUsageEvent(
    event: String,
    entityId: String,
    targetEntityId: String,
    properties: CBUsageProperties,
    eventTime: String)
  extends CBEvent

/* CBGroupInitEvent
{
  "event" : "$set",
  "entityType" : "group"
  "entityId" : "group-1",
  "properties" : {
    "testPeriodStart": "2016-01-02T09:39:45.618-08:00",
    "testPeriodEnd": "2016-02-02T09:39:45.618-08:00",
    "items" : ["item-1", "item-2","item-3", "item-4", "item-5"]
  },
  "eventTime" : "2016-01-02T09:39:45.618-08:00" //Optional
}
*/
case class CBGroupInitProperties (
  testPeriodStart: DateTime, // ISO8601 date
  pageVariants: Seq[String], //["17","18"]
  testPeriodEnd: Option[DateTime])

case class CBGroupInitEvent (
    entityType: String,
    entityId: String,
    properties: CBGroupInitProperties,
    eventTime: String) // ISO8601 date
  extends CBEvent

// allows us to look at what kind of specialized event to create
case class CBRawEvent (
    eventId: String,
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: DateTime, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends CBEvent

trait CBEvent extends Event
