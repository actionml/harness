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
import com.actionml.core.dal.mongo._
import com.actionml.core.model._
import com.actionml.core.storage.Mongo
import com.actionml.core.template.Dataset
import com.actionml.core.validate._
import org.joda.time.DateTime
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.ObjectId
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.{ExecutionContext, Future}
//import org.json4s.{DefaultFormats, Formats, MappingException}

import scala.language.reflectiveCalls

/** Reacts to persisted input data for the Contextual Bandit.
  * There are 2 types of input events for the CB 1) usage events and 2) property change events. The usage events
  * are not stored since this is a Kappa Algorithm but may be mirrored, the property change events translate
  * into changes to mutable DB objects and so are always up-to-date with the last change made. Another way to
  * look at this is that usage events accumulate until the Actor's train creates an updated VW model at which point the
  * model is the only remaining respresentation of the events.
  *
  * See the discussion of Kappa Learning here: https://github.com/actionml/pio-kappa/blob/master/kappa-learning.md
  * This dataset contains collections of users, usage events, and groups. Each get input from the datasets POST
  * endpoint and are parsed and validated but go to different collections, each with different properties. The
  * users and groups are mutable in Mongo, the events are kept as a stream that is truncated after the model is
  * updated so not unnecessary old data is stored.
  *
  * @param engineId REST resource-id from POST /datasets/<resource-id> also ids the mongo DB for all but shared User
  *                   data.
  */
class CBDataset(engineId: String, sharedDB: Option[String] = None)(implicit val injector: Injector)
  extends Dataset[CBEvent](engineId) with JsonParser with Mongo with MongoSupport with AkkaInjectable {

  // how to I get the ExecutionContext that was created in BaseModule in com.actionml.Main?
  // I can't make core depend on server in build.sbt because of circular dependencies and so need to get it
  // some other way, I assume not from injection because injection is provided by BaseModule in the Main.scala
  // file
  implicit val ec = inject[ExecutionContext]
  var users = new UsersDaoImpl(sharedDB.getOrElse(engineId))
  var groups = new CBGroupsDaoImpl(engineId)
  // Users can be shared among Datasets for multiple Engines
  //val u = inject[UsersDao]
  // val usersDAO = UsersDAO(connection(engineId)("users"))
  var usageEventGroups: Map[String, UsageEventDAO] = Map[String, UsageEventDAO]()
  // val groups = store.connection(engineId)("groups") // replaced with CBGroupsDAO
  // may need to createIndex if _id: String messes things up
  //object CBGroupsDAO extends SalatDAO[CBGroup, String](collection = connection(engineId)("groups"))


  override def init(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = Future.successful {
    parseAndValidate[GenericEngineParams](json).andThen { p =>
      groups.list(0, 100).map { groupsIterable =>
        groupsIterable.foreach { group =>
          usageEventGroups = usageEventGroups +
            (group._id -> UsageEventDAO(getDatabase(engineId).getCollection[UsageEvent](group._id)))
        }
      }
      Valid(p)
    }
    Valid(true)
  }

  override def destroy()(implicit ec: ExecutionContext): Future[Unit] = {
    client.getDatabase(engineId).drop.toFuture.map(_ => ())
  }

  // add one json, possibly an CBEvent, to the beginning of the dataset
  override def input(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, CBEvent]] = {
    parseAndValidateInput(json).fold(e => Future.successful(Invalid(e)), persist)
  }


  def persist(event: CBEvent)(implicit ec: ExecutionContext): Future[Validated[ValidateError, CBEvent]] = {
    (event match {
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
                        "contextualTags" -> event.properties.contextualTags,
                        "eventTime" -> new DateTime(event.eventTime)) //sort by this
          */
          for {
            _ <- usageEventGroups(event.properties.testGroupId).insert(event.toUsageEvent)
            _ <- users.addPropValue(event.entityId, "contextualTags", event.properties.contextualTags.getOrElse(Seq.empty))
          } yield Valid(event)
        } else {
          logger.warn(s"Data sent for non-existent group: ${event.properties.testGroupId} will be ignored")
          Future.successful(Invalid(EventOutOfSequence(s"Data sent for non-existent group: ${event.properties.testGroupId}" +
            s" will be ignored")))
        }

      case event: CBUserSetEvent => // user profile update, modifies user object
        logger.debug(s"Dataset: $engineId persisting a User Profile Set Event: $event")
        users.setProperties(event.entityId, event.properties.getOrElse(Map.empty))
          .map(_ => Valid(event))
        //          logger.debug(s"Dataset: $resourceId persisting a User Profile Update Event: $event")
        //          // input to usageEvents collection
        //          // Todo: validate fields first
        //          val updateProps = event.properties.getOrElse(Map.empty)
        //          users.findOne(event.entityId).map { userOpt =>
        //            val user = userOpt.getOrElse(User(event.entityId, Map.empty))
        //            val newProps = user.propsToMapOfSeq ++ updateProps
        //            val newUser = User(user._id, User.propsToMapString(newProps))
        //            val debug = 0
        //            users.update(
        //              DBObject("_id" -> event.entityId),
        //              newUser,
        //              upsert = true,
        //              multi = false,
        //              wc = new WriteConcern)
        //          }.map(_ => Valid(event))
        // TODO: implement ^^^
        //        case event: CBUserUpdateEvent => // user profile update, modifies user object from $set event

      case event: CBUserUnsetEvent => // unset a property in a user profile
        logger.trace(s"Dataset: ${engineId} persisting a User Profile Unset Event: ${event}")
        users.unsetProperties(event.entityId, event.properties.getOrElse(Map.empty).keySet)
          .map(_ => Valid(event))

      case event: CBGroupInitEvent => // group init event, modifies group definition
        logger.trace(s"Persisting a Group Init Event: ${event}")
        // create the events collection for the group
        if (usageEventGroups.contains(event.entityId)) {
          // re-initializing
          usageEventGroups(event.entityId).collection.drop()
        }
        usageEventGroups = usageEventGroups +
          (event.entityId -> UsageEventDAO(getDatabase(engineId).getCollection(event.entityId)))

        groups.insertOne(event.toCBGroup).map(_ => Valid(event))

      //        case event: CBUserUnsetEvent => // unset a property in a user profile
      //          logger.trace(s"Dataset: ${resourceId} persisting a User Profile Unset Event: ${event}")
      //          // input to usageEvents collection
      //          // Todo: validate fields first
      //          val updateProps = event.properties.getOrElse(Map.empty).keySet
      //          val user = usersDAO.findOneById(event.entityId).getOrElse(User(event.entityId, Map.empty))
      //          val newProps = user.propsToMapOfSeq -- updateProps
      //          val newUser = User(user._id, User.propsToMapString(newProps))
      //          val debug = 0
      //          usersDAO.save(newUser).map(_ => Valide(event)) // overwrite the User
      // TODO: implement ^^^
      /*          val unsetPropNames = event.properties.get.keys.toArray

                usersDAO.collection.update(MongoDBObject("_id" -> event.entityId), $unset(unsetPropNames: _*), true)
                Valid(event)
      */
      case event: CBDeleteEvent => // remove an object, Todo: for a group, will trigger model removal in the Engine
        event.entityType match {
          case "user" =>
            logger.trace(s"Dataset: ${engineId} persisting a User Delete Event: ${event}")
            //users.findAndRemove(MongoDBObject("userId" -> event.entityId))
            users.deleteOne(event.entityId)
              .map(_ => Valid(event))
          case "group" | "testGroup" =>
            if ( !usageEventGroups.isDefinedAt(event.entityId) ) {
              logger.warn(s"Deleting non-existent group may be an error, operation ignored.")
              Future.successful(Invalid(ParseError(s"Deleting non-existent group may be an error, operation ignored.")))
            } else {
              groups.deleteOne(event.entityId)
              usageEventGroups(event.entityId).collection.drop.toFuture // drop all events
                .map { _ =>
                  usageEventGroups = usageEventGroups - event.entityId // remove from our collection or collections
                  logger.trace(s"Deleting group ${event.entityId}.")
                  Valid(event)
                }
            }
          case _ =>
            logger.warn(s"Unrecognized $$delete entityType event: ${event} will be ignored")
            Future.successful(Invalid(ParseError(s"Unrecognized event: ${event} will be ignored")))
        }
      case _ =>
        logger.warn(s"Unrecognized event: ${event} will be ignored")
        Future.successful(Invalid(ParseError(s"Unrecognized event: ${event} will be ignored")))
    }).recover {
      case e @ (_ : IllegalArgumentException | _ : ArithmeticException ) =>
        logger.error(s"ISO 8601 Datetime parsing error ignoring input: ${event}", e)
        Invalid(ParseError(s"ISO 8601 Datetime parsing error ignoring input: ${event}"))
      case e: Exception =>
        logger.error(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}", e)
        Invalid(ParseError(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}, ${e.getMessage}"))
    }
  }

  override def parseAndValidateInput(json: String): Validated[ValidateError, CBEvent] = {

    parseAndValidate[CBRawEvent](json).andThen { event =>
      event.event match {
        case "$set" => // either group or user updates
          event.entityType match {
            case "user" => // got a user profile update event
              parseAndValidate[CBUserSetEvent](json)
            case "group" | "testGroup" => // got a group initialize event, uses either new or old name
              logger.trace(s"Dataset: ${engineId} parsing a group init event: ${event.event}")
              parseAndValidate[CBGroupInitEvent](json).map(_.toCBGroup)
          }

        case "$unset" => // remove properties
          event.entityType match {
            case "user" => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing a user unset event: ${event.event}")
              parseAndValidate[CBUserUnsetEvent](json).andThen { uue =>
                if (!uue.properties.isDefined) {
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
              parseAndValidate[CBDeleteEvent](json)
          }

        case _ => // default is a self describing usage event, kept as a stream
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[CBUsageEvent](json)
      }
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

//case class UsersDAO(usersColl: MongoCollection)  extends SalatDAO[User, String](usersColl)

case class CBUserSetEvent(
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
  contextualTags: Option[Seq[String]]
)

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
      converted = this.properties.converted//,
      //eventTime = new DateTime(this.eventTime)
    )
  }
}

case class UsageEvent(
  _id: ObjectId = new ObjectId(),
  event: String,
  userId: String,
  itemId: String,
  testGroupId: String,
  converted: Boolean//,
  //eventTime: DateTime
  )

case class UsageEventDAO(eventColl: MongoCollection[UsageEvent]) {
  def insert(event: UsageEvent): Future[Unit] = ???
  def collection: MongoCollection[UsageEvent] = ???
}

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
  extends CBEvent {

  def toCBGroup: CBGroup = {
    val pvsStringKeyed = this.properties.pageVariants.indices.zip(this.properties.pageVariants).toMap
      .map( t => t._1.toString -> t._2)
    CBGroup(
      _id = this.entityId,
      testPeriodStart = new DateTime(this.properties.testPeriodStart),
      // use the index as the key for the variant string
      pageVariants = pvsStringKeyed,
      testPeriodEnd = if (this.properties.testPeriodEnd.isEmpty) None else Some(new DateTime(this.properties.testPeriodEnd.get))
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
case class CBRawEvent(
    //eventId: String, // not used in Harness, but allowed for PIO compatibility
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Any]] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends CBEvent

