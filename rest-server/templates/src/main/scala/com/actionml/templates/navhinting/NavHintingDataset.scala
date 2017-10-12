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
import com.actionml.utilities.FixedSizeFifo
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import org.joda.time.DateTime
import salat.dao._
import salat.global._

import scala.language.reflectiveCalls

/** Navigation Hinting input data
  * The Dataset manages Users and Journeys. Users are not in-memory. Journeys may be in-memory and persisted
  * immediately after any change.
  *
  * @param engineId REST resource-id from POST /engines/<resource-id>/events also ids the mongo DB for all input
  */
class NavHintingDataset(engineId: String) extends Dataset[NHEvent](engineId) with JsonParser with Mongo {

  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  val usersDAO = UsersDAO(connection(engineId)("users"))
  val activeJourneysDAO: ActiveJourneysDAO = ActiveJourneysDAO(connection(engineId)("active"))
  val completedJourneysDAO: CompletedJourneysDAO = CompletedJourneysDAO(connection(engineId)("completed"))

  var trailLength: Option[Int] = None

  override def init(json: String): Validated[ValidateError, Boolean] = {
    val res = parseAndValidate[GenericEngineParams](json).andThen { p =>
      parseAndValidate[NHAlgoParams](json).andThen { ap =>
        trailLength = Some(ap.numQueueEvents)
        Valid(ap)
      }
      Valid(p) // Todo: trailLength may not have been set if algo params is not valid
    }
    if(res.isInvalid) Invalid(ParseError("Error parsing JSON params for numQueueEvents.")) else Valid(true)
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
          val userOpt = usersDAO.findOneById(event.entityId)
          val conversionOpt = event.properties.conversion
          val unconvertedJourney = activeJourneysDAO.findOneById(event.entityId)
          if(conversionOpt.nonEmpty) {
            // conversion so persist the user's queue or drop if user has converted already
            if(userOpt.nonEmpty && unconvertedJourney.nonEmpty){ // ignore unless we have a trail started for the user
              val newConversionJourney = unconvertedJourney.get.trail :+
                (event.entityId, event.toNavEvent.eventTime.getMillis)
              completedJourneysDAO.insert(Journey(event.entityId, newConversionJourney))
            }
          } else { // no conversion so add event to active journeys and maybe create a user object
            if(userOpt.isEmpty) { // no User fo create empty one
              usersDAO.insert(User(event.entityId))
            }
            val newActiveTrail = unconvertedJourney.get.trail :+ (event.entityId, event.toNavEvent.eventTime.getMillis)
            activeJourneysDAO.insert(Journey(event.entityId, newActiveTrail))
          }
          Valid(event)

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
  properties: Map[String, String] = Map.empty) {
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


case class Journey(
  _id: String, // User-id we are recording nav events for
  trail: FixedSizeFifo[(String, Long)]) // most recent nav events


// active journeys not yet converted
case class ActiveJourneysDAO(activeJourneys: MongoCollection) extends SalatDAO[Journey, String](activeJourneys)

// journeys that have converted and are used in predictions
case class CompletedJourneysDAO(completedJourneys: MongoCollection) extends SalatDAO[Journey, String](completedJourneys)

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
