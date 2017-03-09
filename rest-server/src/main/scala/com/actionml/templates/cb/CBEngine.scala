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

import com.actionml.core.template.{Engine, Params, Query, QueryResult}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.jackson.JsonMethods._
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine(dataset: CBDataset, params: CBEngineParams)
  extends Engine[CBEvent, CBEngineParams, CBQuery, CBQueryResult](dataset, params) with LazyLogging{

  implicit val formats = Formats
  implicit val defaultFormats = DefaultFormats

  def train() = {
    logger.info(s"All data received[${dataset.events.size}]. Start train")
    // get the data
    // get the model
    // update the model with new data
    // train() should be called periodically to avoid training with every event
    // store the model with timestamp
  }

  def input(datum: CBEvent, trainNow: Boolean = true): Boolean = {
    logger.info("Got a single CBEvent: " + datum)
    logger.info("Kappa learning happens every event, starting now.")
    // validation happens as the input goes to the dataset
    dataset.append(datum)
  }

  /** trainNow = false means periodic training occurs triggered by a timer or other method */
  def inputCol(data: Seq[CBEvent], trainNow: Boolean = false): Seq[Boolean] = {
    logger.info("Got a Seq of " + data.size + " Events")
    // Todo: should validate input values and return Seq of Bools indicating that they were validated
    data.map(input(_, false))
    // Todo: should this be periodic or with every event?
    // logger.info("Engine training now.")
    // train()
  }

  def parseAndValidateInput(json: String): (CBEvent, Int) = {
    val event = parse(json).extract[CBEvent]
    (event, 0)
  }

  def parseAndValidateQuery(json: String): (CBQuery, Int) = {
    val query = parse(json).extract[CBQuery]
    (query, 0)
  }


  def query(query: CBQuery): CBQueryResult = {
    logger.info(s"Got a query: $query")
    logger.info("Send query result")
    CBQueryResult()
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
  extends Params

/*
Query
{
  "user": "psmith",
  "testGroupId": "testGroupA"
}

Results
{
  "variant": "variantA",
  "testGroupId": "testGroupA"
}

*/
case class CBQuery(
    user: String,
    groupId: String)
  extends Query

case class CBQueryResult(
    variant: String = "",
    groupId: String = "")
  extends QueryResult

case class Properties (
    testPeriodStart: DateTime, // ISO8601 date
    pageVariants: Seq[String], //["17","18"]
    testPeriodEnd: DateTime) // ISO8601 date

case class CBEvent (
    eventId: String,
    event: String,
    entityType: Option[String] = None,
    entityId: Option[String] = None,
    //properties: Option[Map[String, Any]] = None,
    properties: Option[Properties] = None,
    eventTime: String, // ISO8601 date
    creationTime: String) // ISO8601 date
  extends Query
