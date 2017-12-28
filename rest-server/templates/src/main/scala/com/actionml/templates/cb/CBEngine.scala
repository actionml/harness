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

import cats.data
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.model.{GenericEngineParams, Query, Status}
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidateError, WrongParams}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine() extends Engine() with JsonParser {

  var dataset: CBDataset = _
  var algo: CBAlgorithm = _
  var params: GenericEngineParams = _

  override def init(json: String): Validated[ValidateError, Boolean] = {
    super.init(json).andThen { _ =>
      parseAndValidate[GenericEngineParams](json).andThen { p =>
        params = p
        engineId = params.engineId
        dataset = new CBDataset(engineId)
        algo = new CBAlgorithm(dataset)
        drawInfo("Contextual Bandit Init", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("EngineId: ", engineId),
          ("Mirror Type: ", params.mirrorType),
          ("Mirror Container: ", params.mirrorContainer)))

        Valid(p)
      }.andThen { p =>
        dataset.init(json).andThen { r =>
          algo.init(json, p.engineId)
        }
      }
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
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
    logger.info(s"Waiting for ScaffoldAlgorithm for id: $engineId to terminate")
    algo.stop()
  }

  override def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(CBStatus(
      engineParams = this.params,
      algorithmParams = algo.params,
      activeGroups = algo.trainers.size).toJson)
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
  override def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
    // first detect a batch of events, then process each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.trace("Got JSON body: " + json)
    // validation happens as the input goes to the dataset
    if(super.input(json, trainNow).isValid)
      dataset.input(json).andThen(process).map(_ => true)
    else
      Valid(true) // Some error like an ExecutionError in super.input happened
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
  def process(event: CBEvent): Validated[ValidateError, CBEvent] = {
     event match {
      case event: CBUsageEvent =>
        val datum = CBAlgorithmInput(
          dataset.usersDAO.findOneById(event.toUsageEvent.userId).get,
          event,
          dataset.GroupsDAO.findOneById(event.toUsageEvent.testGroupId).get,
          engineId
        )
        algo.input(datum)
      case event: GroupParams =>
        algo.add(event._id)
      case event: CBDeleteEvent =>
        event.entityType match {
          case "group" | "testGroup" =>
            algo.remove(event.entityId)
          case other => // todo: Pat, need refactoring this, Pat says, no this looks good
            logger.warn("Unexpected value of entityType: {}, in {}", other, event)
        }
      case _ =>
    }
    Valid(event)
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): Validated[ValidateError, String] = {
    logger.trace(s"Got a query JSON string: $json")
    parseAndValidate[CBQuery](json).andThen { query =>
      // query ok if training group exists or group params are in the dataset
      if(algo.trainers.isDefinedAt(query.groupId) || dataset.GroupsDAO.findOneById(query.groupId).nonEmpty) {
        val result = algo.predict(query)
        Valid(result.toJson)
      } else {
        Invalid(WrongParams(s"Query for non-existent group: $json"))
      }
    }
  }

/*  override def status(): String = {
    s"""
      |    Engine: ${this.getClass.getName}
      |    Resource ID: $engineId
      |    Number of active groups: ${algo.trainers.size}
    """.stripMargin
  }
*/
}

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

  def toJson: String = {
    s"""{"variant": $variant, "groupId": $groupId}"""
  }
}

case class CBStatus(
    description: String = "Contextual Bandit Algorithm",
    engineType: String = "Backed by the Vowpal Wabbit compute engine.",
    engineParams: GenericEngineParams,
    algorithmParams: AlgorithmParams,
    activeGroups: Int)
  extends Status {

  def toJson: String = {
    s"""
      |{
      |  "description": $description,
      |  "engineType": $engineType,
      |  "engineParams": $engineParams,
      |  "algorithmParams": $algorithmParams,
      |  "activeGroups": $activeGroups
      |}
    """.stripMargin
  }
}
