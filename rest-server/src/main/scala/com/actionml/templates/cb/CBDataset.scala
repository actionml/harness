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

  implicit val formats = DefaultFormats //needed for json4s parsing
  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  var events = Seq[CBEvent]() // Todo: data will go in a Store eventually

  // These should only be called from trusted source like the CLI!
  def create(name: String = resourceId) = { store.create(name) }
  def destroy() = { store.destroy(resourceId) }

  // add one datum, possibley an CBEvent, to the beginning of the dataset
  def append(datum: CBEvent): Boolean = {
    datum match {
      //case C=> // either group or user updates
      //  logger.info(s"Dataset: ${resourceId} got a special event: ${datum.event}")
      //  datum.event(0) == "$" // modify the object
      case event: CBUsageEvent => // usage data, kept as a stream
        logger.info(s"Dataset: ${resourceId} got a usage event: ${event.event}")
        // append to usageEvents collection
        // Todo: validate fields first
        val eventObj = MongoDBObject(
          UsageEventFieldNames.userId -> event.entityId,
          UsageEventFieldNames.eventName -> event.event,
          UsageEventFieldNames.item -> event.targetEntityId,
          UsageEventFieldNames.group -> event.properties.groupId,
          UsageEventFieldNames.converted -> event.properties.converted,
          UsageEventFieldNames.eventTime -> new DateTime(event.eventTime)) //sort by this

        store.client.getDB(resourceId).getCollection(CollectionNames.usageEvents).insert(eventObj)

        true // store in events collection, return false if there was an error
    }
  }
  // takes a collection of data to append to the Dataset
  def appendAll(data: Seq[CBEvent]): Seq[Boolean] = {
    data.map(append(_))
  }

  def parseAndValidateInput(json: String): (CBEvent, Int) = {
    val event = parse(json).extract[CBRawEvent]
    event.event(0).toString match {
      case "$" => // either group or user updates
        logger.info(s"Dataset: ${resourceId} got a special event: ${event.event}")
        (event, 0) // todo: modify the object
      case _ => // usage data, kept as a stream
        logger.info(s"Dataset: ${resourceId} got a usage event: ${event.event}")
        // append to usageEvents collection
        // Todo: validate fields first
        (parse(json).extract[CBUsageEvent], 0)

    }
  }

}

object CollectionNames {
  val usageEvents = "usage-events"
  val users = "users"
  val groups = "groups"
}

object UsageEventFieldNames {
  val userId = "user-id"
  val eventName = "event-name"
  val item = "item"
  val group = "group"
  val converted = "converted"
  val eventTime = "event-time"
  val properties = "properties"
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
}
 */
case class CBUserUpdateEvent(
  user: String,
  properties: Map[String, Seq[String]],
  eventTime: DateTime)

/* CBUsageEvent some in partially parsed from Json:
{
   "event" : "conversion",
   "entityType" : "user", // ignored
   "entityId" : "amerritt",
   "targetEntityType" : "item", // ignored
   "targetEntityId" : "item-1",
   "properties" : {
      "groupId" : "group-1",
      "converted" : true
    }
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
}
*/
case class CBUsageProperties(
  groupId: String,
  converted: Boolean)

case class CBUsageEvent(
    event: String,
    entityId: String,
    targetEntityId: String,
    properties: CBUsageProperties,
    eventTime: DateTime)
  extends CBEvent

case class CBGroupInitProperties (
    testPeriodStart: DateTime, // ISO8601 date
    pageVariants: Seq[String], //["17","18"]
    testPeriodEnd: DateTime)
  extends CBEvent// ISO8601 date

case class CBRawEvent (
    eventId: String,
    event: String,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    //properties: Option[CBGroupInitProperties] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends CBEvent

trait CBEvent extends Event
