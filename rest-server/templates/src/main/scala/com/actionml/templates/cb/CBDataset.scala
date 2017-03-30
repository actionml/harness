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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Dataset, Event}
import com.actionml.core.validate._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, MappingException}

import scala.language.reflectiveCalls

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
  */
class CBDataset(resourceId: String = "test-resource") extends Dataset[CBEvent](resourceId) {

  private lazy val config = ConfigFactory.load()
  lazy val store = new Mongo(m = config.getString("mongo.host"), p = config.getInt("mongo.port"), n = resourceId)
  // Todo: need to get port from CLI or config

  object CBCollections {
    val users: MongoCollection = store.client.getDB(resourceId).getCollection("users").asScala
    var usageEventGroups: Map[String, MongoCollection] = Map.empty
    val groups: MongoCollection = store.client.getDB(resourceId).getCollection("groups").asScala
  }

  implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all //needed for json4s parsing
  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  // These should only be called from trusted source like the CLI!
  def create(): CBDataset = {
    store.create()
    this
  }

  def destroy(): CBDataset = {
    store.destroy()
    this
  }

  // add one json, possibly an CBEvent, to the beginning of the dataset
  def input(json: String): Validated[ValidateError, CBEvent] = {
    parseAndValidateInput(json).andThen(persist)
  }


  def persist(event: CBEvent): Validated[ValidateError, CBEvent] = {
    try {
      event match {
        case event: CBUsageEvent => // usage data, kept as a stream
          if ( store.client.getDB(resourceId).collectionNames().contains(event.properties.testGroupId) ) {
            logger.debug(s"Dataset: $resourceId persisting a usage event: $event")
            // input to usageEvents collection
            // Todo: validate fields first
            val eventObj = MongoDBObject(
              "user-id" -> event.entityId,
              "event-name" -> event.event,
              "item" -> event.targetEntityId,
              "group" -> event.properties.testGroupId,
              "converted" -> event.properties.converted,
              "eventTime" -> new DateTime(event.eventTime)) //sort by this

            // keep each event stream in it's own collection
            CBCollections.usageEventGroups.get(event.properties.testGroupId) match {
              case Some(collection) ⇒ collection.insert(eventObj)
              case None ⇒ logger.warn("Collection for group '{}' not found!", event.properties.testGroupId)
            }
            Valid(event)
          } else {
            logger.warn(s"Data sent for non-existent group: ${event.properties.testGroupId} will be ignored")
            Invalid(EventOutOfSequence(s"Data sent for non-existent group: ${event.properties.testGroupId}" +
              s" will be ignored"))
          }


        case event: CBUserUpdateEvent => // user profile update, modifies use object
          logger.debug(s"Dataset: $resourceId persisting a User Profile Update Event: $event")
          // input to usageEvents collection
          // Todo: validate fields first
          if (event.properties.nonEmpty) {
            val query = MongoDBObject("user-id" -> event.entityId)
            // replace the old document with the 'apple' instance

            val builder = MongoDBObject.newBuilder
            builder += "userId" -> event.entityId
            builder ++= event.properties.get
            builder += "eventTime" -> new DateTime(event.eventTime)
            val eventObj = builder.result
            CBCollections.users.update(query, eventObj, true, false) // upsert true
            Valid(event)

          } else {
            // no properties so ignore
            logger.warn(s"Dataset: ${resourceId} got an empty User Profile Update Event: ${event} and " +
              s"will be ignored")
            Invalid(ParseError(s"Got an empty User Profile $$set event, ignored."))
          }

        case event: CBGroupInitEvent => // user profile update, modifies use object
          logger.trace(s"Dataset: ${resourceId} persisting a User Profile Update Event: ${event}")
          // create the events collection for the group
          store.client.getDB(resourceId).getCollection(event.entityId).drop() // drop if reinitializing
          CBCollections.usageEventGroups = CBCollections.usageEventGroups +
            (event.entityId -> store.client.getDB(resourceId).getCollection(event.entityId).asScala)

          // Todo: validate fields first?
          val query = MongoDBObject("groupId" -> event.entityId)
          // replace the old document with the 'apple' instance

          val builder = MongoDBObject.newBuilder
          builder += "groupId" -> event.entityId
          builder += "start" -> new DateTime(event.properties.testPeriodStart)
          if (event.properties.testPeriodEnd.nonEmpty) builder += "end" -> new DateTime(event.properties.testPeriodEnd.get)
          builder += "eventTime" -> new DateTime(event.eventTime)
          val eventObj = builder.result
          CBCollections.groups.update(query, eventObj, true, false) // upsert true
          Valid(event)

        case event: CBUserUnsetEvent => // unset a property in a user profile
          logger.trace(s"Dataset: ${resourceId} persisting a User Profile Unset Event: ${event}")
          val update = new MongoDBObject
          val unsetPropNames = event.properties.get.keys.toArray

          CBCollections.users.update(MongoDBObject("userId" -> event.entityId), $unset(unsetPropNames: _*), true)
          Valid(event)

        case event: CBDeleteEvent => // remove an object, Todo: for a group, will trigger model removal in the Engine
          event.entityType match {
            case "user" =>
              logger.trace(s"Dataset: ${resourceId} persisting a User Delete Event: ${event}")
              CBCollections.users.findAndRemove(MongoDBObject("userId" -> event.entityId))
              Valid(event)
            case "group" | "testGroup" =>
              if ( store.client.getDB(resourceId).collectionNames().contains(event.entityId) ) {
                logger.trace(s"Dataset: ${resourceId} persisting a Group Delete Event: ${event}")
                CBCollections.groups.findAndRemove(MongoDBObject("groupId" -> event.entityId))
                store.client.getDB(resourceId).getCollection(event.entityId).drop() // remove all events
                logger.trace(s"Deleting group ${event.entityId}.")
                Valid(event)
              } else {
                logger.warn(s"Deleting non-existent group may be an error, operation ignored.")
                Invalid(ParseError(s"Deleting non-existent group may be an error, operation ignored."))
              }
          }
        case _ =>
          logger.warn(s"Unrecognized event: ${event} will be ignored")
          Invalid(ParseError(s"Unrecognized event: ${event} will be ignored"))
      }
    } catch {
      case e @ (_ : IllegalArgumentException | _ : ArithmeticException ) =>
        logger.error(s"ISO 8601 Datetime parsing error ignoring input: ${event}", e)
        Invalid(ParseError(s"ISO 8601 Datetime parsing error ignoring input: ${event}"))
      case e: Exception =>
        logger.error(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}", e)
        Invalid(ParseError(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}, ${e.getMessage}"))
    }
  }

  def parseAndValidateInput(json: String): Validated[ValidateError, CBEvent] = {

    try {
      val event = parse(json).extract[CBRawEvent]
      event.event match {
        case "$set" => // either group or user updates
          event.entityType match {
            case "user" => // got a user profile update event
              val e = parse(json).extract[CBUserUpdateEvent]
              logger.trace(s"Dataset: ${resourceId} parsing a user update event: ${event.event}")
              if (e.properties.isDefined) {
                Invalid(MissingParams("No parameters specified"))
              } else {
                Valid(e)
              }

            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.trace(s"Dataset: ${resourceId} parsing a group init event: ${event.event}")
              Valid(parse(json).extract[CBGroupInitEvent])
          }

        case "$unset" => // remove properties
          event.entityType match {
            case "user" => // got a user profile update event
              logger.trace(s"Dataset: ${resourceId} parsing a user unset event: ${event.event}")
              val e = parse(json).extract[CBUserUnsetEvent]
              if (e.properties.isDefined) {
                Invalid(MissingParams("No parameters specified"))
              } else {
                Valid(e)
              }
            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.warn(s"Dataset: ${resourceId} parsed a group $$unset event: ${event.event} this is undefined " +
                s"and ignored.")
              Invalid(WrongParams("Group $unset is not allowed and ignored."))
          }

        case "$delete" => // remove an object
          event.entityType match {
            case "user" | "group" | "testGroup" => // got a user profile update event
              logger.trace(s"Dataset: ${resourceId} parsing an $$unset event: ${event.event}")
              Valid(parse(json).extract[CBDeleteEvent])
          }

        case _ => // default is a self describing usage event, kept as a stream
          logger.trace(s"Dataset: ${resourceId} parsing a usage event: ${event.event}")
          Valid(parse(json).extract[CBUsageEvent])
      }
    } catch {
      case e: MappingException =>
        logger.error(s"Json4s parsing error, ignoring malformed event json: ${json}")
        Invalid(ParseError(s"Json4s parsing error, ignoring malformed event json: ${json}"))
    }
   }

}

//case class CBGroupInitProperties( p: Map[String, Seq[String]])

/* CBEvent partially parsed from the Json:
{
  "event" : "$set", //"$unset means to remove some properties (not values) from the object
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
  // Todo:!!! this is the way they should be encoded, fix when we get good JSON
  properties: Option[Map[String, Seq[String]]] = None,
  //properties: Option[Map[String, String]],
  eventTime: String)
  extends CBEvent

case class CBUserUnsetEvent(
  entityId: String,
  // Todo:!!! this is teh way they should be encoded, fix when we get good JSON
  // properties: Option[Map[String, List[String]]],
  properties: Option[Map[String, Any]] = None,
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
  testPeriodStart: String, // ISO8601 date
  pageVariants: Seq[String], //["17","18"]
  testPeriodEnd: Option[String])

case class CBGroupInitEvent (
    entityType: String,
    entityId: String,
    properties: CBGroupInitProperties,
    eventTime: String) // ISO8601 date
  extends CBEvent

/* CBUser Comes in CBEvent partially parsed from the Json:
{
  "event" : "$delete", // removes user: ammerrit
  "entityType" : "user"
  "entityId" : "amerritt",
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
  "creationTime" : "2014-11-02T09:39:45.618-08:00", // ignored, only created by PIO
}
 */
case class CBDeleteEvent(
  entityId: String,
  entityType: String,
  eventTime: String)
  extends CBEvent

// allows us to look at what kind of specialized event to create
case class CBRawEvent (
    eventId: String,
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends CBEvent

trait CBEvent extends Event
