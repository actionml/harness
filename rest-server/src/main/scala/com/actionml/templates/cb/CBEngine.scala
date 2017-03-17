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

import com.actionml.core.template._
import com.actionml.router.http.HTTPStatusCodes
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
    logger.info(s"Start train")
    // get the data
    // get the model
    // update the model with new data
    // train() should be called periodically to avoid training with every event
    // store the model with timestamp
  }

  /** Triggers parse, validation, and persistence of event encoded in the json */
  def input(json: String, trainNow: Boolean = true): Int = {
    // first detect a batch of events, then process each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.trace("Got JSON body: " + json)
    // validation happens as the input goes to the dataset
    dataset.input(json)
    // train() // for kappa?
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): (CBQueryResult, Int) = {
    logger.info(s"Got a query JSON string: ${json}")
    logger.info("Send query result")
    (CBQueryResult(), HTTPStatusCodes.ok)
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
