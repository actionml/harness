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
class CBEngine(dataset: CBDataset, params: CBEngineParams)
  extends Engine[CBEvent, CBQueryResult](dataset, params) with LazyLogging{

  lazy val algo = new CBAlgorithm(getAlgoParams(params)).init() // this auto-starts kappa training on dataset in params

  implicit val formats = DefaultFormats  ++ JodaTimeSerializers.all //needed for json4s parsing
  RegisterJodaTimeConversionHelpers() // registers Joda time conversions used to serialize objects to Mongo

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

  def parseAndValidateQuery(json: String): Validated[ValidateError, CBQueryResult] = {
    logger.trace(s"Got a query JSON string: ${json}")
    try{
      Valid(parse(json).extract[CBQueryResult])
    } catch {
      case e: MappingException =>
        logger.error(s"Recoverable Error: malformed query: ${json}", e)
        Invalid(ParseError(s"Json4s parsing error, malformed query json: ${json}"))

    }
  }

  def getAlgoParams(ep: CBEngineParams): CBAlgoParams = {
    CBAlgoParams(
      dataset,
      ep.maxIter,
      ep.regParam,
      ep.stepSize,
      ep.bitPrecision,
      ep.modelName,
      ep.namespace,
      ep.maxClasses)
  }

}

case class CBEngineParams(
    id: String = "", // required
    dataset: String = "", // required, readFile now
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelName: String = "model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
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
