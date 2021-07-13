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

package com.actionml.engines.urnavhinting

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.{HIO, drawInfo}
import com.actionml.core.engine.{Engine, QueryResult}
import com.actionml.core.jobs.{JobDescription, JobManager}
import com.actionml.core.model.{EngineParams, Event, Query, Response}
import com.actionml.core.store.Ordering._
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.store.indexes.annotations.SingleIndex
import com.actionml.core.validate.{JsonSupport, ValidRequestExecutionError, ValidateError}
import com.actionml.engines.urnavhinting.URNavHintingEngine.{URNavHintingEngineParams, URNavHintingEvent, URNavHintingQuery}
import org.json4s.JValue
import zio.IO

import scala.concurrent.{ExecutionContext, Future}


class URNavHintingEngine extends Engine with JsonSupport {

  private var dataset: URNavHintingDataset = _
  private var algo: URNavHintingAlgorithm = _
  private var params: URNavHintingEngineParams = _

  /** Initializing the Engine sets up all needed objects */
  override def init(jsonConfig: String, update: Boolean = false): Validated[ValidateError, Response] = {
    super.init(jsonConfig).andThen { _ =>
      parseAndValidate[URNavHintingEngineParams](jsonConfig).andThen { p =>
        params = p
        engineId = params.engineId
        val dbName = p.sharedDBName.getOrElse(engineId)
        dataset = new URNavHintingDataset(
          engineId = engineId,
          store = MongoStorage.getStorage(dbName, MongoStorageHelper.codecs),
          p.sharedDBName.isEmpty)
        val eventsDao = dataset.store.createDao[URNavHintingEvent](dataset.getIndicatorEventsCollectionName)
        algo = URNavHintingAlgorithm(this, jsonConfig, dataset, eventsDao)
        logStatus(p)
        Valid(p)
      }.andThen { p =>
        dataset.init(jsonConfig).andThen { r =>
          algo.init(this)
        }
      }
    }
  }

  def logStatus(p: URNavHintingEngineParams) = {
    drawInfo("URNavHinting Engine", Seq(
      ("════════════════════════════════════════", "══════════════════════════════════════"),
      ("Engine ID: ", engineId),
      ("Mirror type: ", p.mirrorType),
      ("Mirror Container: ", p.mirrorContainer),
      ("Shared DB name: ", p.sharedDBName)))
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  // Todo: should return null for bad init
  override def initAndGet(jsonConfig: String, update: Boolean): URNavHintingEngine = {
    val response = init(jsonConfig, update)
    if (response.isValid) {
      logger.trace(s"Initialized with JSON: $jsonConfig")
      this
    } else {
      logger.error(s"Parse error with JSON: $jsonConfig")
      null.asInstanceOf[URNavHintingEngine] // todo: ugly, replace
    }
  }

  override def input(jsonEvent: String): Validated[ValidateError, Response] = {
    logger.trace("Got JSON body: " + jsonEvent)
    // validation happens as the input goes to the dataset
    //super.input(jsonEvent).andThen(_ => dataset.input(jsonEvent)).andThen { _ =>
    super.input(jsonEvent).andThen(_ => dataset.input(jsonEvent)).andThen { _ =>
      parseAndValidate[URNavHintingEvent](jsonEvent).andThen(algo.input)
    }
    //super.input(jsonEvent).andThen(dataset.input(jsonEvent)).andThen(algo.input(jsonEvent)).map(_ => true)
  }

  // todo: should merge base engine status with URNavHintingEngine's status
  override def status(): HIO[Response] = {
    logStatus(params)
    IO.effect(URNavHintingEngineStatus(params, JobManager.getActiveJobDescriptions(engineId)))
      .mapError { e =>
        logger.error("Get status error", e)
        ValidRequestExecutionError("Get status error")
      }
  }

  override def train(implicit ec: ExecutionContext): Validated[ValidateError, Response] = {
    algo.train(ec)
  }

  /** triggers parse, validation of the query then returns the result as JSONharness */
  override def query(jsonQuery: String): Validated[ValidateError, URNavHintingEngine.URNavHintingQueryResult] = {
    logger.trace(s"Got a query JSON string: $jsonQuery")
    parseAndValidate[URNavHintingQuery](jsonQuery).andThen { query =>
      val result = algo.query(query)
      Valid(result)
    }
  }

  override def queryAsync(jsonQuery: String)(implicit ec: ExecutionContext): Future[Response] = Future.failed(new NotImplementedError())

  // todo: should kill any pending Spark jobs
  override def destroy(): Unit = {
    logger.info(s"Dropping persisted data for id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  override def getUserData(userId: String, num: Int, from: Int): Validated[ValidateError, List[Response]] =
    throw new NotImplementedError

  override def deleteUserData(userId: String): HIO[Response] = throw new NotImplementedError
}

object URNavHintingEngine {
  def apply(jsonConfig: String, isNew: Boolean): URNavHintingEngine = {
    val engine = new URNavHintingEngine()
    engine.initAndGet(jsonConfig, isNew)
  }

  case class URNavHintingEngineParams(
      engineId: String, // required, resourceId for engine
      engineFactory: String,
      mirrorType: Option[String] = None,
      mirrorContainer: Option[String] = None,
      sharedDBName: Option[String] = None,
      sparkConf: Map[String, JValue])
    extends EngineParams {

    import org.json4s._
    import org.json4s.jackson.Serialization
    import org.json4s.jackson.Serialization.write

    implicit val formats = Serialization.formats(NoTypeHints)

    def toJson: String = {
      write(this)
    }

  }

  case class URNavHintingEvent (
      //eventId: String, // not used in Harness, but allowed for PIO compatibility
      event: String,
      entityType: String,
      @SingleIndex(order = asc, isTtl = false) entityId: String,
      targetEntityId: Option[String] = None,
      properties: Map[String, Boolean] = Map.empty,
      conversionId: Option[String] = None, // only set when copying converted journey's where event = nav-event
      @SingleIndex(order = desc, isTtl = true) eventTime: String) // ISO8601 date
    extends Event with Serializable

  case class ItemProperties (
      _id: String, // must be the same as the targetEntityId for the $set event that changes properties in the model
      properties: Map[String, Any] // properties to be written to the model, this is saved in the input dataset
  ) extends Serializable

  case class URNavHintingQuery(
      user: String, // ignored for non-personalized
      eligibleNavIds: Array[String])
    extends Query

  case class URNavHintingQueryResult(
      navHints: Seq[(String, Double)] = Seq.empty)
    extends Response with QueryResult {

    def toJson: String = {
      val jsonStart =
        s"""
           |{
           |  "result": [
        """.stripMargin
      val jsonMiddle = navHints.map{ case (k, v) =>
        s"""
           |   {$k, $v},
       """.stripMargin
      }.mkString
      val jsonEnd =
        s"""
           |  ]
           |}
        """.stripMargin
      val retVal = jsonStart + jsonMiddle + jsonEnd
      retVal
    }
  }

}

case class URNavHintingEngineStatus(engineParams: URNavHintingEngineParams, jobStatuses: Iterable[JobDescription]) extends Response
