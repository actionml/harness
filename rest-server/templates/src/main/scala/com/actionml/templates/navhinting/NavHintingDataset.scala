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

package com.actionml.templates.navhinting

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Dataset, Event, GenericEngineParams}
import com.actionml.core.validate._
import com.mongodb.casbah.Imports._
import org.joda.time.DateTime
import salat.dao._
import salat.global._
//import org.json4s.{DefaultFormats, Formats, MappingException}

import scala.language.reflectiveCalls

/** DAO for the Navigation Hinting input data
  * The Dataset manages Users and Journeys. Users are not in-memory. Journeys are in-memory and persisted
  * immediately after any change. Journeys are implemented as a PriorityQueue of Tuple2s of an id and weight.
  * The weigth it calculated by a decay function to order the queue, which is of fixed length. See the
  * NavHintingAlgorithm for queue usage in model creation.
  *
  * @param engineId REST resource-id from POST /engines/<resource-id>/events also ids the mongo DB for all input
  */
class NavHintingDataset(engineId: String) extends Dataset[NHEvent](engineId) with JsonParser with Mongo {

  val usersDAO = UsersDAO(connection(engineId)("users"))
  var usageEventGroups: Map[String, NavEventDAO] = Map[String, NavEventDAO]()
  // val groups = store.connection(engineId)("groups") // replaced with GroupsDAO
  // may need to createIndex if _id: String messes things up


  // These should only be called from trusted source like the CLI!
  override def init(json: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[GenericEngineParams](json).andThen { p =>
      GroupsDAO.find(MongoDBObject("_id" -> MongoDBObject("$exists" -> true))).foreach { p =>
        usageEventGroups = usageEventGroups +
          (p._id -> NavEventDAO(connection(engineId)(p._id)))
      }
      Valid(p)
    }
    Valid(true)
  }

  override def destroy() = {
    client.dropDatabase(engineId)
  }

  // add one json, possibly an NHEvent, to the beginning of the dataset
  override def input(json: String): Validated[ValidateError, NHEvent] = {
    parseAndValidateInput(json).andThen(persist)
  }


  def persist(event: NHEvent): Validated[ValidateError, NHEvent] = {
    try {
      event match {
        case event: NHNavEvent => // nav events enqued for each user until conversion
          if (usageEventGroups.keySet.contains(event.properties.testGroupId)) {
            logger.debug(s"Dataset: $engineId persisting a usage event: $event")
            // input to usageEvents collection
            // Todo: validate fields first
/*            val eventObj = MongoDBObject(
              "userId" -> event.entityId,
              "eventName" -> event.event,
              "itemId" -> event.targetEntityId,
              "eligibleNavIds" -> event.properties.testGroupId,
              "conversion" -> event.properties.conversion,
              "eventTime" -> new DateTime(event.eventTime)) //sort by this
*/
            usageEventGroups(event.properties.testGroupId).insert(event.toNavEvent)
            Valid(event)
          } else {
            logger.warn(s"Data sent for non-existent group: ${event.properties.testGroupId} will be ignored")
            Invalid(EventOutOfSequence(s"Data sent for non-existent group: ${event.properties.testGroupId}" +
              s" will be ignored"))
          }


        case event: NHUserUpdateEvent => // user profile update, modifies user object
          logger.debug(s"Dataset: $engineId persisting a User Profile Update Event: $event")
          // input to usageEvents collection
          // Todo: validate fields first
          val props = event.properties.getOrElse(Map.empty)
          val userProps = User.propsToMapString(props)
          usersDAO.insert(User(event.entityId, userProps))
          Valid(event)

        case event: NHUserUnsetEvent => // unset a property in a user profile
          logger.trace(s"Dataset: ${engineId} persisting a User Profile Unset Event: ${event}")
          val update = new MongoDBObject
          val unsetPropNames = event.properties.get.keys.toArray

          usersDAO.collection.update(MongoDBObject("userId" -> event.entityId), $unset(unsetPropNames: _*), true)
          Valid(event)

        case event: HNDeleteEvent => // remove an object, Todo: for a group, will trigger model removal in the Engine
          event.entityType match {
            case "user" =>
              logger.trace(s"Dataset: ${engineId} persisting a User Delete Event: ${event}")
              //users.findAndRemove(MongoDBObject("userId" -> event.entityId))
              usersDAO.removeById(event.entityId)
              Valid(event)
            case _ =>
              logger.warn(s"Unrecognized $$delete entityType event: ${event} will be ignored")
              Invalid(ParseError(s"Unrecognized event: ${event} will be ignored"))
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

  override def parseAndValidateInput(json: String): Validated[ValidateError, NHEvent] = {

    parseAndValidate[NHRawEvent](json).andThen { event =>
      event.event match {
        case "$set" => // either group or user updates
          event.entityType match {
            case "user" => // got a user profile update event
              parseAndValidate[NHUserUpdateEvent](json)
          }

        case "$unset" => // remove properties
          event.entityType match {
            case "user" => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing a user unset event: ${event.event}")
              parseAndValidate[NHUserUnsetEvent](json).andThen { uue =>
                if (uue.properties.isDefined) {
                  Invalid(MissingParams("No parameters specified, event ignored"))
                } else {
                  Valid(uue)
                }
              }
            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.warn(s"Dataset: ${engineId} parsed a group $$unset event: ${event.event} this is undefined " +
                s"and ignored.")
              Invalid(WrongParams("Group $unset is not allowed and ignored."))
          }

        case "$delete" => // remove an object
          event.entityType match {
            case "user" | "group" | "testGroup" => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing an $$delete event: ${event.event}")
              parseAndValidate[HNDeleteEvent](json)
          }

        case _ => // default is a self describing usage event, kept as a stream
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[NHNavEvent](json)
      }
    }
  }

}

//case class CBGroupInitProperties( p: Map[String, Seq[String]])

/* NHEvent partially parsed from the Json:
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

case class User(
  _id: String,
  properties: Map[String, String]) {
  //def toSeq = properties.split("%").toSeq // in case users have arrays of values for a property, salat can't handle
  def propsToMapOfSeq = properties.map { case(propId, propString) =>
    propId -> propString.split("%").toSeq
  }
}


object User { // convert the Map[String, Seq[String]] to Map[String, String] by encoding the propery values in a single string
  def propsToMapString(props: Map[String, Seq[String]]): Map[String, String] = {
    props.filter { (t) =>
      t._2.size != 0 && t._2.head != ""
    }.map { case (propId, propSeq) =>
      propId -> propSeq.mkString("%")
    }
  }
}

case class UsersDAO(usersColl: MongoCollection)  extends SalatDAO[User, String](usersColl)

case class NHUserUpdateEvent(
    entityId: String,
    // Todo:!!! this is the way they should be encoded, fix when we get good JSON
    properties: Option[Map[String, Seq[String]]] = None,
    //properties: Option[Map[String, String]],
    eventTime: String)
  extends NHEvent

case class NHUserUnsetEvent(
    entityId: String,
    // Todo:!!! this is teh way they should be encoded, fix when we get good JSON
    // properties: Option[Map[String, Seq[String]]],
    properties: Option[Map[String, Any]] = None,
    eventTime: String)
  extends NHEvent

/*
Some values are ignored
{
  "event" : "nav-event",
  "entityType" : "user", // value ignored
  "entityId" : "pferrel",
  "targetEntityType" : "???", // value ignored
  "targetEntityId" : "nav-1", // assumed to be a nav-event-id
  "properties" : {
    "conversion" : true | false
  }
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
}
*/
case class NHNavEventProperties(
  conversion: String)

case class NHNavEvent(
    event: String,
    entityId: String,
    targetEntityId: String,
    properties: NHNavEventProperties,
    eventTime: String)
  extends NHEvent {

  def toNavEvent: NavEvent = {
    NavEvent(
      event = this.event,
      userId = this.entityId,
      itemId = this.targetEntityId,
      converted = this.properties.conversion,
      eventTime = new DateTime(this.eventTime)
    )
  }
}

case class NavEvent(
  _id: ObjectId = new ObjectId(),
  event: String,
  userId: String,
  itemId: String,
  converted: String,
  eventTime: DateTime)

case class NavEventDAO(eventColl: MongoCollection) extends SalatDAO[NavEvent, ObjectId](eventColl)


/* HNUser Comes in NHEvent partially parsed from the Json:
{
  "event" : "$delete", // removes user: ammerrit
  "entityType" : "user"
  "entityId" : "amerritt",
  "eventTime" : "2014-11-02T09:39:45.618-08:00",
  "creationTime" : "2014-11-02T09:39:45.618-08:00", // ignored, only created by PIO
}
 */
case class HNDeleteEvent(
  entityId: String,
  entityType: String,
  eventTime: String)
  extends NHEvent

// allows us to look at what kind of specialized event to create
case class NHRawEvent (
    //eventId: String, // not used in Harness, but allowed for PIO compatibility
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends NHEvent

trait NHEvent extends Event
