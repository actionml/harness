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
import com.actionml.core.template.{Engine, EngineParams, Query, QueryResult}
import com.actionml.core.validate.{ParseError, ValidateError}
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.typesafe.scalalogging.LazyLogging
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, MappingException}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine() extends Engine() with LazyLogging with JsonParser {

  implicit val formats = DefaultFormats  ++ JodaTimeSerializers.all //needed for json4s parsing
  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

  def init(json: String): Validated[ValidateError, Boolean] = {
    algo = new CBAlgorithm(getAlgoParams(params))
    val response = parseAndValidate[CBEngineParams](json)
    if (response.isValid) {
      engineId = response.getOrElse[CBEngineParams](CBEngineParams()).
      algo.init(json)

    } else response.map(_.)
  }

  def train(): Unit = {
    logger.trace(s"Only used for Lambda style training")
  }

  /** Triggers parse, validation, and persistence of event encoded in the json */
  def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
    // first detect a batch of events, then process each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.trace("Got JSON body: " + json)
    // validation happens as the input goes to the dataset
    dataset.input(json).andThen(triggerAlgorithm).map(_ => true)
    // Just as only the engine know training is triggered on input, also some events may cause the model to change
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
  def triggerAlgorithm(event: CBEvent): Validated[ValidateError, CBEvent] = {
     event match {
      case event: CBUsageEvent =>
        algo.train(event.properties.testGroupId)
      case event: CBGroupInitEvent =>
        algo.add(event.entityId,dataset.CBCollections.usageEventGroups(event.entityId))
      case event: CBDeleteEvent =>
        event.entityType match {
          case "group" | "testGroup" =>
            algo.remove(event.entityId)
          case other => // todo: Pat, need refactoring this
            logger.warn("Non expected value of entityType: {}, in {}", other, event)
        }
      case _ =>
    }
    Valid(event)
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): Validated[ValidateError, CBQueryResult] = {
    logger.trace(s"Got a query JSON string: ${json}")
    Valid(CBQueryResult())
  }

  def parseAndValidate[T](json: String): Validated[ValidateError, T] = {
    try{
      Valid(parse(json).extract[CBQueryResult])
    } catch {
      case e: MappingException =>
        logger.error(s"Malformed json: ${json}", e)
        Invalid(ParseError(s"Json4s parsing error, malformed json: ${json}"))

    }
  }

}

case class CBEngineParams(
    engineId: String = "") // required
  extends EngineParams

/*
Query
{
  "user": "psmith",
  "testGroupId": "testGroupA"
}
*/
case class CBQuery(
    user: String,
    groupId: String)
  extends Query

/*
Results
{
  "variant": "variantA",
  "testGroupId": "testGroupA"
}
*/
case class CBQueryResult(
    variant: String = "",
    groupId: String = "")
  extends QueryResult
