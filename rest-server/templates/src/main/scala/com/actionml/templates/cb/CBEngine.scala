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
import com.actionml.core.backup.Mirroring
import com.actionml.core.template.{Engine, EngineParams, Query, QueryResult}
import com.actionml.core.validate.{JsonParser, ParseError, ValidateError}
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.typesafe.scalalogging.LazyLogging
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, MappingException}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine() extends Engine() with JsonParser with Mirroring {

  val dataset = new CBDataset()
  override val algo: CBAlgorithm = new CBAlgorithm(dataset)
  var params: CBEngineParams = _

  override def init(json: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[CBEngineParams](json).andThen { p =>
      params = p
      Valid(p)
    }.map(_ => true)
  }

  // used when init might fail from bad params in the json but you want an Engine, not a Validated
  // Todo: should return null for bad init
  override def initAndGet(json: String): CBEngine = {
    val response = init(json)
    if (response.isValid) {
      logger.trace(s"Initialized with JSON: $json")
      this
    } else {
      logger.error(s"Parse error with JSON: $json")
      null.asInstanceOf[CBEngine] // todo: ugly, replace
    }
  }

  override def stop(): Unit = {
    logger.info(s"Waiting for CBAlgorithm for id: $engineId to terminate")
    algo.stop() // Todo: should have a timeout and do something on timeout here
  }

  override def destroy(): Unit = {
    logger.info(s"Dropping persisted data for id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  def train(): Unit = {
    logger.warn(s"Only used for Lambda style training")
  }

  /** Triggers parse, validation, and persistence of event encoded in the json */
  def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
    // first detect a batch of events, then process each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.trace("Got JSON body: " + json)
    mirrorJson(json)
    // validation happens as the input goes to the dataset
    dataset.input(json).andThen(process(_)).map(_ => true)
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
  def process(event: CBEvent): Validated[ValidateError, CBEvent] = {
     event match {
      case event: CBUsageEvent =>
        algo.train(event.properties.testGroupId)
      case event: CBGroupInitEvent =>
        algo.add(event.entityId,dataset.usageEventGroups(event.entityId))
      case event: CBDeleteEvent =>
        event.entityType match {
          case "group" | "testGroup" =>
            algo.remove(event.entityId)
          case other => // todo: Pat, need refactoring this
            logger.warn("Unexpected value of entityType: {}, in {}", other, event)
        }
      case _ =>
    }
    Valid(event)
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): Validated[ValidateError, String] = {
    logger.trace(s"Got a query JSON string: ${json}")
    Valid(CBQueryResult("variant1", "group1").toJson)
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
  extends QueryResult {

  def toJson() = {
  s"""
     |"variant": $variant,
     |"groupId": $groupId
   """.stripMargin
  }
}
