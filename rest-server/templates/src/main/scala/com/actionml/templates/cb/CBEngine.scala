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
import com.actionml.core.core.drawInfo
import com.actionml.core.dal.UsersDao
import com.actionml.core.dal.mongo.MongoSupport
import com.actionml.core.model._
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidateError, WrongParams}
import scaldi.{Injector, Module}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

// Kappa style calls train with each input, may wait for explicit triggering of train for Lambda
class CBEngine(override implicit val injector: Injector) extends Engine with JsonParser {

  var dataset: CBDataset = _
  var algo: CBAlgorithm = _
  var params: GenericEngineParams = _


  override def init(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    super.init(json).flatMap { _ =>
      parseAndValidate[GenericEngineParams](json).andThen { p =>
        params = p
        engineId = params.engineId
        dataset = new CBDataset(engineId, Some(p.sharedDBName.getOrElse(engineId)))
        algo = new CBAlgorithm(dataset)
        drawInfo("Contextual Bandit Init", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("EngineId: ", engineId),
          ("Mirror Type: ", params.mirrorType),
          ("Mirror Container: ", params.mirrorContainer)))

        Valid(p)
      }.fold(e => Future.successful(Invalid(e)), { p =>
        dataset.init(json).flatMap { r =>
          algo.init(json, p.engineId)
        } //( _ => algo.init(json, engineId))
      })
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  override def initAndGet(json: String)(implicit ec: ExecutionContext): Future[CBEngine] = {
    init(json).map { response =>
      if (response.isValid) {
        logger.trace(s"Initialized with JSON: $json")
        this
      } else {
        logger.error(s"Parse error with JSON: $json")
        null.asInstanceOf[CBEngine] // todo: ugly, replace
      }
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

  override def destroy()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Dropping persisted data for id: $engineId")
    for {
      _ <- dataset.destroy
      _ <- algo.destroy
    } yield ()
  }

  def train(): Unit = {
    logger.warn(s"Only used for Lambda style training")
  }

  /** Triggers parse, validation, and processing of event encoded in the json */
  override def input(json: String, trainNow: Boolean = true)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    // first detect a batch of events, then process each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.trace("Got JSON body: " + json)
    // validation happens as the input goes to the dataset
    super.input(json, trainNow).flatMap { a =>
      if (a.isValid)
        dataset.input(json).flatMap(_.fold(e => Future.successful(Invalid(e)), event => process(event))).map(_ => Valid(true))
      else
        Future.successful(Valid(true)) // Some error like an ExecutionError in super.input happened
    }
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
  def process(event: CBEvent)(implicit ec: ExecutionContext): Future[Validated[ValidateError, CBEvent]] = {
     event match {
      case event: CBUsageEvent =>
        val datum = CBAlgorithmInput(
          dataset.users.findOne(event.toUsageEvent.userId),
          event,
          dataset.groups.findOne(event.toUsageEvent.testGroupId),
          engineId
        )
        algo.input(datum)
      case event: CBGroup =>
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
    Future.successful(Valid(event))
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, String]] = {
    logger.trace(s"Got a query JSON string: $json")
    parseAndValidate[CBQuery](json).fold(e => Future.successful(Invalid(e)), query =>
      if (algo.trainers.isDefinedAt(query.groupId)) {
        algo.predict(query).map(result => Valid(result.toJson))
      } else {
        Future.successful(Invalid(WrongParams(s"Query for non-existent group: $json")))
      }
    )
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
