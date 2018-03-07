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
import com.actionml.core.model.{Event, GenericEngineParams, User}
import com.actionml.core.storage.Mongo
import com.actionml.core.template.Dataset
import org.bson.BsonString
import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{BsonDocument, Document, ObjectId}

import scala.concurrent.{ExecutionContext, Future}
//import com.actionml.core.template.{Dataset, Event}
import com.actionml.core.validate._
import org.joda.time.DateTime

import scala.language.reflectiveCalls

/** Navigation Hinting input data
  * The Dataset manages Users and Journeys. Users are not in-memory. Journeys may be in-memory and persisted
  * immediately after any change.
  *
  * @param engineId REST resource-id from POST /engines/<resource-id>/events also ids the mongo DB for all input
  */
class NavHintingDataset(engineId: String) extends Dataset[NHEvent](engineId) with JsonParser with Mongo {

  private val codecs: List[CodecProvider] = {
    import org.mongodb.scala.bson.codecs.Macros._
    List(classOf[Journey], classOf[Hints])
  }

  val activeJourneysDAO: ActiveJourneysDAO = ActiveJourneysDAO(getDatabase(engineId, codecs).getCollection[Journey]("active_journeys"))
  val navHintsDAO: NavHintsDAO = NavHintsDAO(getDatabase(engineId, codecs).getCollection("nav_hints"))

  //var navHintsDAO: SalatDAO[Map[String, Double], String] = _

  var trailLength: Int = _

  override def init(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = Future.successful {
    val res = parseAndValidate[GenericEngineParams](json).andThen { p =>
      parseAndValidate[NHAlgoParams](json).andThen { algoParams =>
        trailLength = algoParams.numQueueEvents.getOrElse(50)
        Valid(algoParams)
      }
      Valid(p) // Todo: trailLength may not have been set if algo params is not valid
    }
    if(res.isInvalid) Invalid(ParseError("Error parsing JSON params for numQueueEvents.")) else Valid(true)
  }

  override def destroy()(implicit ec: ExecutionContext): Future[Unit] = {
    client().getDatabase(engineId).drop.toFuture.map(_ => ())
  }

  // add one json, possibly an NHEvent, to the beginning of the dataset
  override def input(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, NHEvent]] = {
    parseAndValidateInput(json).fold(e => Future.successful(Invalid(e)), persist)
  }


  def persist(event: NHEvent)(implicit ec: ExecutionContext): Future[Validated[ValidateError, NHEvent]] = {
    try {
      event match {
        case event: NHNavEvent => // nav events enqued for each user until conversion

          activeJourneysDAO.findOneById(event.entityId).flatMap { unconvertedJourney =>

            val conversion = event.properties.conversion.getOrElse(false)
            if (!conversion) { // store in the user journey queue
              if (unconvertedJourney.nonEmpty) {
                val updatedJourney = enqueueAndUpdate(event, unconvertedJourney)
                if (updatedJourney.nonEmpty) { // existing Journey so updAte in place
                  activeJourneysDAO
                    .save(updatedJourney.get)
                    .map(_ => Valid(true))
                } // else the first event for the journey is a conversion so ignore
              } else { // no persisted journey so create it
                activeJourneysDAO.insert(
                  Journey(
                    event.entityId,
                    Seq(EventTime(event.targetEntityId, DateTime.parse(event.eventTime)))
                  )
                ).map(_ => Valid(true))
              }
              Future.successful(Valid(event))
            } else {
              Future.successful(Valid(event))
            }
          }

        case event: HNDeleteEvent => // remove an object, Todo: for a group, will trigger model removal in the Engine
          event.entityType match {
            case "user" =>
              logger.trace(s"Dataset: ${engineId} removing any journey data for user: ${event.entityId}")
              activeJourneysDAO
                .removeById(event.entityId)
                .map(_ => Valid(event))
            case _ =>
              logger.warn(s"Unrecognized $$delete entityType event: ${event} will be ignored")
              Future.successful(Invalid(ParseError(s"Unrecognized event: ${event} will be ignored")))
          }
        case _ =>
          logger.warn(s"Unrecognized event: ${event} will be ignored")
          Future.successful(Invalid(ParseError(s"Unrecognized event: ${event} will be ignored")))
      }
    } catch {
      case e @ (_ : IllegalArgumentException | _ : ArithmeticException ) =>
        logger.error(s"ISO 8601 Datetime parsing error ignoring input: ${event}", e)
        Future.successful(Invalid(ParseError(s"ISO 8601 Datetime parsing error ignoring input: ${event}")))
      case e: Exception =>
        logger.error(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}", e)
        Future.successful(Invalid(ParseError(s"Unknown Exception: Beware! trying to recover by ignoring input: ${event}, ${e.getMessage}")))
    }
  }

  override def parseAndValidateInput(json: String): Validated[ValidateError, NHEvent] = {

    parseAndValidate[NHRawEvent](json).andThen { event =>
      event.event match {
        case "$delete" => // remove an object
          event.entityType match {
            case "user"  => // got a user profile update event
              logger.trace(s"Dataset: ${engineId} parsing an $$delete event: ${event.event}")
              parseAndValidate[HNDeleteEvent](json)
          }

        case _ => // default is a self describing usage event, kept as a stream
          logger.trace(s"Dataset: ${engineId} parsing a usage event: ${event.event}")
          parseAndValidate[NHNavEvent](json)
      }
    }
  }

  def enqueueAndUpdate(event: NHNavEvent, maybeJourney: Option[Journey]): Option[Journey] = {
    if (maybeJourney.nonEmpty) {
      val journey = maybeJourney.get
      Some(
        Journey(
          journey._id,
          (journey.trail :+ EventTime(event.targetEntityId, DateTime.parse(event.eventTime))).takeRight(trailLength)
        )
      )
    } else None
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

case class UsersDAO(usersColl: MongoCollection[User])

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
  conversion: Option[Boolean] = Some(false))

case class NHNavEvent(
    event: String,
    entityId: String,
    targetEntityId: String,
    properties: NHNavEventProperties,
    eventTime: String)
  extends NHEvent {

/*  def toNavEvent: NavEvent = {
    NavEvent(
      _id = this.event,
      userId = this.entityId,
      itemId = this.targetEntityId,
      converted = this.properties.conversion.getOrElse(false),
      eventTime = new DateTime(this.eventTime)
    )
  }
*/
}

case class NavEvent(
  _id: ObjectId = new ObjectId(),
  event: String,
  userId: String,
  itemId: String,
  converted: String,
  eventTime: DateTime)

case class NavEventDAO(eventColl: MongoCollection[NavEvent])

case class EventTime(eventId: String, time: DateTime)
case class Journey(
  _id: String, // User-id we are recording nav events for
  trail: Seq[EventTime]) // most recent nav events, a PriorityQueue would be ideal here but maybe overkill.

// active journeys not yet converted
case class ActiveJourneysDAO(col: MongoCollection[Journey]) {
  import Util.mkIdDoc

  def find(search: Document)(implicit ec: ExecutionContext): Future[Iterable[Journey]] = col.find(search.toBsonDocument).toFuture

  def findOneById(id: String)(implicit ec: ExecutionContext): Future[Option[Journey]] = {
    col.find(mkIdDoc(id))
      .toFuture
      .map(_.headOption)
  }
  def insert(journey: Journey)(implicit ec: ExecutionContext): Future[Unit] = col.insertOne(journey).toFuture().map(_ => ())

  def save(journey: Journey)(implicit ec: ExecutionContext): Future[Unit] = col.replaceOne(mkIdDoc(journey._id), journey).toFuture.map(_ => ())

  def removeById(id: String)(implicit ec: ExecutionContext): Future[Unit] = col.deleteOne(mkIdDoc(id)).toFuture.map(_ => ())

}

private object Util {
  def mkIdDoc(id: String)(implicit ec: ExecutionContext): BsonDocument = BsonDocument(Seq("_id" -> new BsonString(id)))
}

// model = sum of converted jouney weighted vectors
case class Hints(hints: Map[String, Double], _id: String = "1")
case class NavHintsDAO(navHints: MongoCollection[Hints]) {
  def findOne(search: Document)(implicit ec: ExecutionContext): Future[Option[Hints]] = navHints.find(search).headOption
  def save(hints: Hints)(implicit ec: ExecutionContext): Future[Unit] = navHints.replaceOne(Util.mkIdDoc(hints._id), hints).toFuture.map(_ => ())
}

case class NavHint(
    _id: String = "", // nav-id
    score: Double = Double.MinPositiveValue) // scored nav-ids

/* HNUser Comes in NHEvent partially parsed from the Json:
{
  "event" : "$delete", // removes user: ammerrit
  "entityType" : "user"
  "entityId" : "pferrel",
}
 */
case class HNDeleteEvent(
    entityId: String,
    entityType: String)
  extends NHEvent

// allows us to look at what kind of specialized event to create
case class NHRawEvent (
    //eventId: String, // not used in Harness, but allowed for PIO compatibility
    event: String,
    entityType: String,
    entityId: String,
    targetEntityId: Option[String] = None,
    properties: Option[Map[String, Boolean]] = None,
    eventTime: String) // ISO8601 date
  extends NHEvent

trait NHEvent extends Event
