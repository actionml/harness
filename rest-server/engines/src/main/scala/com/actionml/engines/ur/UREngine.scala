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

package com.actionml.engines.ur

import java.util.Date
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.{HIO, drawInfo}
import com.actionml.core.engine.{Engine, QueryResult}
import com.actionml.core.jobs.{JobDescription, JobManager}
import com.actionml.core.model.{Comment, EngineParams, Event, Query, Response}
import com.actionml.core.store.Ordering._
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.store.indexes.annotations.{CompoundIndex, SingleIndex}
import com.actionml.core.validate.{JsonSupport, ParseError, ValidRequestExecutionError, ValidateError}
import com.actionml.engines.ur.URAlgorithm.URAlgorithmParams
import com.actionml.engines.ur.URDataset.URDatasetParams
import com.actionml.engines.ur.UREngine.{UREngineParams, UREvent, URQuery}
import org.json4s.JValue
import zio.{IO, Task, ZIO}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class UREngine extends Engine with JsonSupport {

  private var dataset: URDataset = _
  private var algo: URAlgorithm = _
  private var params: UREngineParams = _

  /** Initializing the Engine sets up all needed objects */
  override def init(jsonConfig: String, update: Boolean = false): Validated[ValidateError, Response] = {
    super.init(jsonConfig).andThen { _ =>
      parseAndValidate[UREngineParams](jsonConfig).andThen { p =>
        import ExecutionContext.Implicits.global
        params = p
        engineId = params.engineId
        val dbName = p.sharedDBName.getOrElse(engineId)
        dataset = new URDataset(engineId = engineId, store = MongoStorage.getStorage(dbName, MongoStorageHelper.codecs))
        if (update) parseAndValidate[URDatasetParams](
          jsonConfig,
          errorMsg = s"Error in the Dataset part pf the JSON config for engineId: $engineId, which is: " +
            s"$jsonConfig",
          transform = _ \ "dataset"
        ).foreach { p =>
          def eventsDao = dataset.store.createDao[UREvent](dataset.getEventsCollectionName)
          p.ttl.fold[Validated[ValidateError, _]] {
            eventsDao.createIndexesAsync(365.days).onComplete {
              case Success(_) => logger.info("Indexes created successfully with default ttl")
              case Failure(e) => logger.error("Create index error (with default ttl)", e)
            }
            Valid(p)
          } { ttlString =>
            Try(Duration(ttlString)).toOption.map { ttl =>
              // We assume the DAO will check to see if this is a change and no do the reindex if not needed
              eventsDao.createIndexesAsync(ttl).onComplete {
                case Success(_) => logger.info(s"Indexes created successfully with ttl=$ttlString")
                case Failure(e) => logger.error(s"Create index error (ttl=$ttlString)", e)
              }
              logger.debug(s"Got a dataset.ttl = $ttl")
              Valid(p)
            }.getOrElse(Invalid(ParseError(s"Can't parse ttl $ttlString")))
          }
        }
        algo = URAlgorithm(this, jsonConfig, dataset)
        logStatus(p)
        Valid(p)
      }.andThen { p =>
        dataset.init(jsonConfig).andThen { r =>
          algo.init(this)
        }
      }
    }
  }

  def logStatus(p: UREngineParams) = {
    drawInfo("UR Engine", Seq(
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
  override def initAndGet(jsonConfig: String, update: Boolean): UREngine = {
    val response = init(jsonConfig, update)
    if (response.isValid) {
      logger.info(s"Engine-id: ${engineId}. Initialized with JSON: $jsonConfig")
      this
    } else {
      logger.error(s"Engine-id: ${engineId}. Parse error with JSON: $jsonConfig")
      null.asInstanceOf[UREngine] // todo: ugly, replace
    }
  }

  override def input(jsonEvent: String): Validated[ValidateError, Response] = {
    //logger.info("Got JSON body: " + jsonEvent)
    // validation happens as the input goes to the dataset
    //super.input(jsonEvent).andThen(_ => dataset.input(jsonEvent)).andThen { _ =>
    val response = super.input(jsonEvent)
      .andThen( _ => dataset.input(jsonEvent))
      .andThen( _ => parseAndValidate[UREvent](jsonEvent)
        .andThen(algo.input))


    //super.input(jsonEvent).andThen(dataset.input(jsonEvent)).andThen(algo.input(jsonEvent)).map(_ => true)
    if(response.isInvalid) logger.info(s"Engine-id: ${engineId}. Bad input ${response.getOrElse(" Whoops, no response string ")}")// else logger.info("Good input")
    response
  }

  override def inputAsync(jsonEvent: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Response]] = {
    // missing behavior from parseAndValidate, super.input, and algo.input
    // this disables things like mirroring (super.input)
    for {
      response <- super.inputAsync(jsonEvent).map { _.andThen { _ =>
        parseAndValidate[UREvent](jsonEvent)
      }.andThen(algo.input)}
      r <- if (response.isValid) dataset.inputAsync(jsonEvent).fold(a => Future.successful(Invalid(a)), _.map(a => Valid(a)))
           else Future.successful(response)
    } yield r
  }

  override def inputMany(data: Seq[String]): Task[Unit] = {
    super.inputMany(data).as(dataset.inputMany(data))
  }

  // todo: should merge base engine status with UREngine's status
  override def status(): HIO[Response] = {
    logStatus(params)
    IO.effect(UREngineStatus(params, JobManager.getActiveJobDescriptions(engineId)))
      .mapError { e =>
        logger.error("Get status error", e)
        ValidRequestExecutionError("Get status error")
      }
  }

  override def train(implicit ec: ExecutionContext): Validated[ValidateError, Response] = {
    algo.train(ec)
  }

  /** triggers parse, validation of the query then returns the result as JSONharness */
  def query(jsonQuery: String): Validated[ValidateError, Response] = {
    logger.trace(s"Engine-id: ${engineId}. Got a query JSON string: $jsonQuery")
    parseAndValidate[URQuery](jsonQuery).andThen { query =>
      val result = algo.query(query)
      Valid(result)
    }
  }

  def queryAsync(jsonQuery: String)(implicit ec: ExecutionContext): Future[Response] = {
    logger.trace(s"Engine-id: ${engineId}. Got a query JSON string: $jsonQuery")
    parseAndValidate[URQuery](jsonQuery) match {
      case Invalid(error) => Future.failed(new RuntimeException(s"Parse error. Can't parse $jsonQuery. Cause: $error"))
      case Valid(t) => algo.queryAsync(t)
    }
  }

  // todo: should kill any pending Spark jobs
  override def destroy: Unit = {
    super.destroy()
    logger.info(s"Dropping persisted data for Engine-id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  override def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    algo.cancelJob(engineId, jobId)
  }

  override def getUserData(userId: String, num: Int, from: Int): Validated[ValidateError, List[Response]] =
    dataset.getUserData(userId, num, from)

  override def deleteUserData(userId: String): HIO[Response] = {
    dataset.deleteUserData(userId)
  }
}

object UREngine extends JsonSupport {
  def apply(jsonConfig: String, isNew: Boolean): UREngine = {
    val engine = new UREngine()
    engine.initAndGet(jsonConfig, update = isNew)
  }

  case class UREngineParams(
      engineId: String, // required, resourceId for engine
      engineFactory: String,
      mirrorType: Option[String] = None,
      mirrorContainer: Option[String] = None,
      sharedDBName: Option[String] = None,
      sparkConf: Map[String, JValue],
      algorithm: URAlgorithmParams,
      dataset: Option[URDataset])
    extends EngineParams {

    import org.json4s.jackson.Serialization.write
    /*
    implicit val formats = Serialization.formats(NoTypeHints)
    */

    def toJson: String = {
      write(this)
    }

  }

  @CompoundIndex(List("entityId" -> asc, "eventTime" -> desc))
  case class UREvent (
      eventId: Option[String], // not used in Harness, but allowed for PIO compatibility
      event: String,
      entityType: String,
      @SingleIndex(order = asc, isTtl = false) entityId: String,
      targetEntityId: Option[String] = None,
      dateProps: Map[String, Date] = Map.empty,
      categoricalProps: Map[String, Seq[String]] = Map.empty,
      floatProps: Map[String, Float] = Map.empty,
      booleanProps: Map[String, Boolean] = Map.empty,
      @SingleIndex(order = desc, isTtl = true) eventTime: Date)
    extends Event with Serializable with Response

  case class URItemProperties (
    @SingleIndex(order = asc, isTtl = false) _id: String, // must be the same as the targetEntityId for the $set event that changes properties in the model
    dateProps: Map[String, Date] = Map.empty, // properties to be written to the model, this is saved in the input dataset
    categoricalProps: Map[String, Seq[String]] = Map.empty,
    floatProps: Map[String, Float] = Map.empty,
    booleanProps: Map[String, Boolean] = Map.empty
  ) extends Serializable

  case class URQuery(
      user: Option[String] = None, // must be a user or item id
      userBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      item: Option[String] = None, // must be a user or item id
      itemBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      itemSet: Option[Seq[String]] = None, // item-set query, shpping cart for instance.
      itemSetBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      rules: Option[Seq[Rule]] = None, // default: whatever is in algorithm params or None
      currentDate: Option[String] = None, // if used will override dateRange filter, currentDate must lie between the item's
      // expireDateName value and availableDateName value, all are ISO 8601 dates
      dateRange: Option[DateRange] = None, // optional before and after filter applied to a date field
      blacklistItems: Option[Seq[String]] = None, // default: whatever is in algorithm params or None
      returnSelf: Option[Boolean] = None, // means for an item query should the item itself be returned, defaults
      // to what is in the algorithm params or false
      num: Option[Int] = None, // default: whatever is in algorithm params, which itself has a default--probably 20
      from: Option[Int] = None, // paginate from this position return "num"
      indicatorNames: Option[Seq[String]], // names used to ID all user indicatorRDDs
      withRanks: Option[Boolean] = None) // Add to ItemScore rank rules values, default false
    extends Query

  /** Used to specify how Fields are represented in engine.json */
  case class Rule( // no optional values for rules, when specified
    name: String, // name of metadata field
    values: Seq[String], // rules can have multiple values like tags of a single value as when using hierarchical
    // taxonomies
    bias: Float) // any positive value is a boost, negative is an inclusion filter, 0 is an exclusion filter

  /** Used to specify the date range for a query */
  case class DateRange(
    name: String, // name of item property for the date comparison
    before: Option[String], // empty strings means no filter
    after: Option[String]) // both empty should be ignored

  case class ItemScore(
    item: ItemID, // item id
    score: Double, // used to rank, original score returned from teh search engine
    ranks: Option[Map[String, Double]] = None)

  case class URQueryResult(
      result: Seq[ItemScore] = Seq.empty)
    extends Response with QueryResult {

    def toJson: String = {
      import org.json4s.jackson.Serialization.write

      write(this)
    }
  }

}

case class UREngineStatus(
    engineParams: UREngineParams,
    jobStatuses: Iterable[JobDescription])
  extends Response
