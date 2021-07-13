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

package com.actionml.engines.navhinting

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.{HIO, drawInfo}
import com.actionml.core.model.{Comment, GenericEngineParams, Query, Response}
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.engine._
import com.actionml.core.validate.{JsonSupport, ValidateError}
import zio.IO

import scala.concurrent.{ExecutionContext, Future}

/** Controller for Navigation Hinting. Trains with each input in parallel with serving queries */
class NavHintingEngine extends Engine with JsonSupport {

  var dataset: NavHintingDataset = _
  var algo: NavHintingAlgorithm = _
  var params: GenericEngineParams = _

  override def init(json: String, update: Boolean = false): Validated[ValidateError, Response] = {
    super.init(json).andThen { _ =>
      parseAndValidate[GenericEngineParams](json).andThen { p =>
        params = p
        engineId = params.engineId
        import scala.concurrent.ExecutionContext.Implicits.global
        dataset = new NavHintingDataset(engineId, MongoStorage.getStorage(engineId, MongoStorageHelper.codecs))
        drawInfo("Navigation Hinting Init", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("EngineId: ", engineId),
          ("Mirror Type: ", params.mirrorType),
          ("Mirror Container: ", params.mirrorContainer),
          ("All Parameters:", params)))

        Valid(jsonComment("NavHintingEngine initialized"))
      }.andThen { _ =>
        dataset.init(json).andThen { _ =>
          if (!update) { // do this when creating rather than updating
            algo = new NavHintingAlgorithm(json, dataset)
            algo.init(this)
          } else Valid(Comment("NavHintingAlgorithm updated"))
        }
      }
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  // Todo: should return null for bad init
  override def initAndGet(json: String, update: Boolean): NavHintingEngine = {
   val response = init(json, update)
    if (response.isValid) {
      logger.trace(s"Initialized with Engine's JSON: $json")
      this
    } else {
      logger.error(s"Parse error with Engine's JSON: $json")
      null.asInstanceOf[NavHintingEngine] // todo: ugly, replace
    }
  }

  override def status(): HIO[NavHintingStatus] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    IO.succeed(NavHintingStatus(
      engineParams = this.params,
      algorithmParams = algo.params))
  }

  override def destroy(): Unit = {
    logger.info(s"Dropping persisted data for id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  /** Triggers parse, validation, and persistence of event encoded in the json */
  override def input(json: String): Validated[ValidateError, Response] = {
    // first detect a batch of events, then persist each, parse and validate then persist if needed
    // Todo: for now only single events pre input allowed, eventually allow an array of json objects
    logger.debug("Got JSON body: " + json)
    // validation happens as the input goes to the dataset
    super.input(json).andThen(_ => dataset.input(json).andThen(process)).map(_ => Comment("NavHinting input processed"))
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated against the system by this time */
  def process(event: NHEvent): Validated[ValidateError, NHEvent] = {
     event match {
      case event: NHNavEvent =>
        algo.input(NavHintingAlgoInput(event, engineId))
      case _ => logger.debug(s"Ignoring not nav-hinting nav event - $event")// anything else has already been dealt with by other parts of the input flow
    }
    Valid(event)
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): Validated[ValidateError, NHQueryResult] = {
    logger.debug(s"Got a query JSON string: $json")
    parseAndValidate[NHQuery](json).andThen { query =>
      // query ok if training group exists or group params are in the dataset
      Valid(algo.query(query))
    }
  }

  def queryAsync(json: String)(implicit ec: ExecutionContext): Future[Response] = Future.failed(new NotImplementedError())

  override def getUserData(userId: String, num: Int, from: Int): Validated[ValidateError, List[Response]] =
    throw new NotImplementedError

  override def deleteUserData(userId: String): HIO[Response] = throw new NotImplementedError
}

case class NHQuery(
    userId: Option[String], // ignored for non-personalized
    eligibleNavIds: Array[String])
  extends Query

case class NHQueryResult(
    navHints: Array[(String, Double)])
  extends Response with QueryResult {

  def toJson: String = {
    val jsonStart = s"""
     |{
     |    "results": [
    """.stripMargin
    val jsonMiddle = navHints.map{ case (k, v) =>
      s"""
         | {$k, $v},
       """.stripMargin
    }.mkString
    val jsonEnd =
      s"""
         |]}
       """.stripMargin
    val retVal = jsonStart + jsonMiddle + jsonEnd
    retVal
  }
}

case class NavHintingStatus(
    description: String = "Navigation Hinting Algorithm",
    engineType: String = "Simple analytical discovery of likely conversion paths",
    engineParams: GenericEngineParams,
    algorithmParams: AlgorithmParams)
  extends Response

object NavHintingEngine {
  def apply(json: String, isNew: Boolean): NavHintingEngine = {
    val engine = new NavHintingEngine()
    engine.initAndGet(json, update = isNew)
  }
}
