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

package com.actionml.engines.cb

import java.time.OffsetDateTime
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.HIO
import com.actionml.core.engine.SharedUserDataset
import com.actionml.core.jobs.JobDescription
import com.actionml.core.model._
import com.actionml.core.store.{DAO, Store}
import com.actionml.core.utils.DateTimeUtil
import com.actionml.core.validate._

import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.util.control.NonFatal

/** Reacts to persisted input data for the Contextual Bandit.
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
  * @param engineId REST resource-id from POST /datasets/<resource-id> also ids the mongo DB for all but shared User
  *                   data.
  */
class CBDataset(engineId: String, storage: Store, usersStorage: Store) extends SharedUserDataset[CBEvent](engineId, usersStorage) with JsonSupport {
  import com.actionml.core.store.DaoQuery.syntax._

  var usageEventGroups: Map[String, DAO[UsageEvent]] = Map[String, DAO[UsageEvent]]()

  val groupsDao = storage.createDao[GroupParams]("groups")

  // These should only be called from trusted source like the CLI!
  override def init(json: String, update: Boolean = false): Validated[ValidateError, Response] = {
    // super.init will handle the users collection, allowing sharing of user data between Engines
    super.init(json, update).andThen { _ =>
      this.parseAndValidate[GenericEngineParams](json).andThen { p =>
        groupsDao.findOne().foreach { p =>
          usageEventGroups = usageEventGroups + (p._id -> storage.createDao[UsageEvent](p._id))
        }
        Valid(Comment("Initialized CBDataset"))
      }
    }
  }

  override def destroy() = {
    storage.drop()
  }

  // add one json, possibly an CBEvent, to the beginning of the dataset
  override def input(json: String): Validated[ValidateError, CBEvent] = {
    parseAndValidate[CBRawEvent](json).andThen { event =>
      event.event match {
        case "$set" => // either group or user updates
          event.entityType match {
            case "user" => // got a user profile update event
              parseAndValidate[CBUserUpdateEvent](json)
            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.trace(s"Dataset: ${engineId} parsing a group init event: ${event.event}")
              parseAndValidate[CBGroupInitEvent](json).map(_.toGroupParams)
          }

        case "$unset" => // removeOne properties
          event.entityType match {
            case "user" => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing a user $$unset event: ${event.event}")
              parseAndValidate[CBUserUnsetEvent](json).andThen { uue =>
                if (!uue.properties.isDefined) {
                  Invalid(MissingParams(jsonComment("No parameters specified, event ignored")))
                } else {
                  Valid(uue)
                }
              }
            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.warn(s"Dataset: ${engineId} parsed a group $$unset event: ${event.event} this is undefined " +
                s"and ignored.")
              Invalid(WrongParams(jsonComment("Group $unset is not allowed and ignored.")))
          }

        case "$delete" => // removeOne an object
          event.entityType match {
            case "user" | "group" | "testGroup" => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing an $$delete event: ${event.event}")
              parseAndValidate[CBDeleteEvent](json)
          }

        case _ => // default is a self describing usage event, kept as a stream
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[CBUsageEvent](json)
      }
    }.andThen(persist)
  }


  def persist(event: CBEvent): Validated[ValidateError, CBEvent] = {
    try {
      event match {
        case event: CBUsageEvent => // usage data, kept as a stream
          if (usageEventGroups.keySet.contains(event.properties.testGroupId)) {
            logger.debug(s"Dataset: $engineId persisting a usage event: $event")
            // input to usageEvents collection
            // Todo: validate fields first
            /*            val eventObj = MongoDBObject(
              "userId" -> event.entityId,
              "eventName" -> event.event,
              "itemId" -> event.targetEntityId,
              "groupId" -> event.properties.testGroupId,
              "converted" -> event.properties.converted,
              "eventTime" -> new DateTime(event.eventTime)) //sort by this
*/
            usageEventGroups(event.properties.testGroupId).saveOneById(event.entityId, event.toUsageEvent)

            if (event.properties.converted) { // store the preference with the user
              val user = usersDAO.findOneById(event.entityId).getOrElse(User("", Map.empty))
              val existingProps = user.propsToMapOfSeq
              val updatedUser = if (existingProps.keySet.contains("contextualTags")) {
                val newTags = (existingProps("contextualTags") ++ event.properties.contextualTags).takeRight(100)
                val newTagString = newTags.mkString("%")
                val newProps = user.properties + ("contextualTags" -> newTagString)
                User(user._id, newProps)
              } else {
                val newTags = event.properties.contextualTags.takeRight(100).mkString("%")
                User(user._id, user.properties + ("contextualTags" -> newTags))
              }
              usersDAO.saveOneById(user._id, updatedUser)
            }
            Valid(event)

          } else {
            logger.warn(s"Data sent for non-existent group: ${event.properties.testGroupId} will be ignored")
            Invalid(EventOutOfSequence(jsonComment(s"Data sent for non-existent group: ${event.properties.testGroupId}" +
              s" will be ignored")))
          }


        case event: CBUserUpdateEvent => // user profile update, modifies user object from $set event
          logger.debug(s"Dataset: $engineId persisting a User Profile Update Event: $event")
          // input to usageEvents collection
          // Todo: validate fields first
          val updateProps = event.properties.getOrElse(Map.empty)
          val user = usersDAO.findOneById(event.entityId).getOrElse(User(event.entityId, Map.empty))
          val newProps = user.propsToMapOfSeq ++ updateProps
          val newUser = User(user._id, User.propsToMapString(newProps))
          usersDAO.saveOneById(event.entityId, newUser)
          Valid(event)

        case event: GroupParams => // group init event, modifies group definition
          logger.trace(s"Persisting a Group Init Event: ${event}")
          // create the events collection for the group
          if (usageEventGroups.contains(event._id)) {
            // re-initializing
            storage.removeCollection(event._id)
          }
          usageEventGroups = usageEventGroups + (event._id -> storage.createDao[UsageEvent](event._id))

          groupsDao.saveOneById(event._id, event)
          Valid(event)

        case event: CBUserUnsetEvent => // unset a property in a user profile
          logger.trace(s"Dataset: ${engineId} persisting a User Profile Unset Event: ${event}")
          // input to usageEvents collection
          // Todo: validate fields first
          val updateProps = event.properties.getOrElse(Map.empty).keySet
          val user = usersDAO.findOneById(event.entityId).getOrElse(User(event.entityId, Map.empty))
          val newProps = user.propsToMapOfSeq -- updateProps
          val newUser = User(user._id, User.propsToMapString(newProps))
          usersDAO.saveOneById(user._id, newUser) // overwrite the User

          Valid(event)
        /*          val unsetPropNames = event.properties.get.keys.toArray

          usersDAO.collection.update(MongoDBObject("_id" -> event.entityId), $unset(unsetPropNames: _*), true)
          Valid(event)
*/
        case event: CBDeleteEvent => // removeOne an object, Todo: for a group, will trigger model removal in the Engine
          event.entityType match {
            case "user" =>
              logger.trace(s"Dataset: ${engineId} persisting a User Delete Event: ${event}")
              //users.findAndRemove(MongoDBObject("userId" -> event.entityId))
              usersDAO.removeOne("_id" === event.entityId)
              Valid(event)
            case "group" | "testGroup" =>
              if (!usageEventGroups.isDefinedAt(event.entityId)) {
                logger.warn(s"Deleting non-existent group may be an error, operation ignored.")
                Invalid(ParseError(jsonComment(s"Deleting non-existent group may be an error, operation ignored.")))
              } else {
                groupsDao.removeOne("_id" === event.entityId)
                storage.removeCollection(event.entityId)
                usageEventGroups = usageEventGroups - event.entityId // removeOne from our collection or collections
                logger.trace(s"Deleting group ${event.entityId}.")
                Valid(event)
              }
            case _ =>
              logger.warn(s"Unrecognized $$delete entityType event: ${event} will be ignored")
              Invalid(ParseError(jsonComment(s"Unrecognized event: ${event} will be ignored")))
          }
        case _ =>
          logger.warn(s"Unrecognized event: ${event} will be ignored")
          Invalid(ParseError(jsonComment(s"Unrecognized event: ${event} will be ignored")))
      }
    } catch {
      case e@(_: IllegalArgumentException | _: ArithmeticException) =>
        logger.error(s"ISO 8601 Datetime parsing error ignoring input: ${event}", e)
        Invalid(ParseError(jsonComment(s"ISO 8601 Datetime parsing error ignoring input: ${event}")))
      case NonFatal(e) =>
        logger.error(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}", e)
        Invalid(ParseError(jsonComment(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}, ${e.getMessage}")))
    }
  }

  override def inputAsync(datum: String): Validated[ValidateError, Future[Response]] = Invalid(NotImplemented())

  override def getUserData(userId: String, num: Int, from: Int): Validated[ValidateError, List[Response]] =
    throw new NotImplementedError

  override def deleteUserData(userId: String): HIO[JobDescription] =
    throw new NotImplementedError
}


//case class CBGroupInitProperties( p: Map[String, Seq[String]])

/* CBEvent partially parsed from the Json:
{
  "event" : "$set", //"$unset means to removeOne some properties (not values) from the object
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
  properties: Option[Map[String, Int]] = None,
  eventTime: String)
  extends CBEvent

/* ScaffoldUsageEvent string
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
    converted: Boolean,
    contextualTags: Seq[String])

case class CBUsageEvent(
    event: String,
    entityId: String,
    targetEntityId: String,
    properties: CBUsageProperties//,
    //eventTime: String
    )
  extends CBEvent {

  def toUsageEvent: UsageEvent = {
    UsageEvent(
      event = this.event,
      userId = this.entityId,
      itemId = this.targetEntityId,
      testGroupId = this.properties.testGroupId,
      converted = this.properties.converted,
      contextualTags = this.properties.contextualTags
      //eventTime = new DateTime(this.eventTime)
    )
  }
}

case class UsageEvent(
    event: String,
    userId: String,
    itemId: String,
    testGroupId: String,
    converted: Boolean,
    contextualTags: Seq[String]
  )

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

case class GroupParams (
  _id: String,
  testPeriodStart: OffsetDateTime, // ISO8601 date
  pageVariants: Map[String, String], //((1 -> "17"),(2 -> "18"))
  testPeriodEnd: Option[OffsetDateTime]) extends CBEvent {

  def keysToInt(v: Map[String, String]): Map[Int, String] = {
    v.map( a => a._1.toInt -> a._2)
  }
}


case class CBGroupInitEvent (
    entityType: String,
    entityId: String,
    properties: CBGroupInitProperties,
    eventTime: String) // ISO8601 date
  extends CBEvent {

  def toGroupParams: GroupParams = {
    val pvsStringKeyed = this.properties.pageVariants.indices.zip(this.properties.pageVariants).toMap
      .map( t => t._1.toString -> t._2)
    GroupParams(
      _id = this.entityId,
      testPeriodStart = DateTimeUtil.parseOffsetDateTime(this.properties.testPeriodStart),
      // use the index as the key for the variant string
      pageVariants = pvsStringKeyed,
      testPeriodEnd = if (this.properties.testPeriodEnd.isEmpty) None else Some(DateTimeUtil.parseOffsetDateTime(this.properties.testPeriodEnd.get))
    )
  }
}


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
    //eventId: String, // not used in Harness, but allowed for PIO compatibility
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends CBEvent

trait CBEvent extends Event
