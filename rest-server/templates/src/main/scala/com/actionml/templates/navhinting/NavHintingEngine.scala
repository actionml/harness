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
import com.actionml.core.model.{GenericEngineParams, Query, Status}
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidateError, WrongParams}

/** Controller for Navigation Hinting. Trains with each input in parallel with serving queries */
//TODO: implement with new asyn API
//class NavHintingEngine() extends Engine() with JsonParser {
//
//  var dataset: NavHintingDataset = _
//  var algo: NavHintingAlgorithm = _
//  var params: GenericEngineParams = _
//
//  override def init(json: String): Validated[ValidateError, Boolean] = {
//    super.init(json).andThen { _ =>
//      parseAndValidate[GenericEngineParams](json).andThen { p =>
//        params = p
//        engineId = params.engineId
//        dataset = new NavHintingDataset(engineId)
//        algo = new NavHintingAlgorithm(dataset)
//        drawInfo("Navigation Hinting Init", Seq(
//          ("════════════════════════════════════════", "══════════════════════════════════════"),
//          ("EngineId: ", engineId),
//          ("Mirror Type: ", params.mirrorType),
//          ("Mirror Container: ", params.mirrorContainer)))
//
//        Valid(p)
//      }.andThen { p =>
//        dataset.init(json).andThen { r =>
//          algo.init(json, this)
//        }
//      }
//    }
//  }
//
//  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
//  // the administrator.
//  // Todo: This method for re-init or new init needs to be refactored, seem ugly
//  // Todo: should return null for bad init
//  override def initAndGet(json: String): NavHintingEngine = {
//   val response = init(json)
//    if (response.isValid) {
//      logger.trace(s"Initialized with Engine's JSON: $json")
//      this
//    } else {
//      logger.error(s"Parse error with Engine's JSON: $json")
//      null.asInstanceOf[NavHintingEngine] // todo: ugly, replace
//    }
//  }
//
//  override def stop(): Unit = {
//    logger.info(s"Waiting for NavHintingAlgorithm with id: $engineId to terminate")
//    algo.stop()
//  }
//
//  override def status(): Validated[ValidateError, String] = {
//    logger.trace(s"Status of base Engine with engineId:$engineId")
//    Valid(NavHintingStatus(
//      engineParams = this.params,
//      algorithmParams = algo.params).toJson)
//  }
//
//  override def destroy(): Unit = {
//    logger.info(s"Dropping persisted data for id: $engineId")
//    dataset.destroy()
//    algo.destroy()
//  }
//
//  def train(): Unit = {
//    logger.warn(s"Only used for Lambda style training")
//  }
//
//  /** Triggers parse, validation, and persistence of event encoded in the json */
//  override def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
//    // first detect a batch of events, then persist each, parse and validate then persist if needed
//    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
//    logger.trace("Got JSON body: " + json)
//    // validation happens as the input goes to the dataset
//    if(super.input(json, trainNow).isValid)
//      dataset.input(json).andThen(process).map(_ => true)
//    else
//      Valid(true)
//  }
//
//  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
//  def process(event: NHEvent): Validated[ValidateError, NHEvent] = {
//     event match {
//      case event: NHNavEvent =>
//        algo.input(NavHintingAlgoInput(event, engineId))
//      case _ => // anything else has already been dealt with by other parts of the input flow
//    }
//    Valid(event)
//  }
//
//  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
//  def query(json: String): Validated[ValidateError, String] = {
//    logger.trace(s"Got a query JSON string: $json")
//    parseAndValidate[NHQuery](json).andThen { query =>
//      // query ok if training group exists or group params are in the dataset
//      Valid( algo.predict(query).toJson)
//    }
//  }
//
//}
//
//case class NHQuery(
//    userId: Option[String], // ignored for non-personalized
//    eligibleNavIds: Array[String])
//  extends Query
//
//case class NHQueryResult(
//    navHints: Array[(String, Double)])
//  extends QueryResult {
//
//  def toJson: String = {
//    val jsonStart = s"""
//     |{
//     |    "results": [
//    """.stripMargin
//    val jsonMiddle = navHints.map{ case (k, v) =>
//      s"""
//         | {$k, $v},
//       """.stripMargin
//    }.mkString
//    val jsonEnd =
//      s"""
//         |]}
//       """.stripMargin
//    val retVal = jsonStart + jsonMiddle + jsonEnd
//    retVal
//  }
//}
//
//case class NavHintingStatus(
//    description: String = "Navigation Hinting Algorithm",
//    engineType: String = "Simple analytical discovery of likely conversion paths",
//    engineParams: GenericEngineParams,
//    algorithmParams: AlgorithmParams)
//  extends Status {
//
//  def toJson: String = {
//    s"""
//      |{
//      |  "description": $description,
//      |  "engineType": $engineType,
//      |  "engineParams": $engineParams,
//      |  "algorithmParams": $algorithmParams,
//      |}
//    """.stripMargin
//  }
//}
