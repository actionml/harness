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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.engine._
import com.actionml.core.jobs.JobManager
import com.actionml.core.model.{GenericQuery, GenericQueryResult}
import com.actionml.core.spark.SparkContextSupport
import com.actionml.core.store.SparkMongoSupport
import com.actionml.core.validate.{JsonParser, MissingParams, ValidateError, WrongParams}
import com.actionml.engines.ur.URAlgorithm.{DefaultIndicatorParams, DefaultURAlgoParams, Field, RankingParams, URAlgorithmParams}
import com.actionml.engines.ur.UREngine.{ItemProperties, UREvent}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext, rdd}
import org.bson.Document
import org.joda.time.DateTime
import com.actionml.core.store.backends.MongoStorage
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global

/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class URAlgorithm private (engine: UREngine, initParams: String, dataset: URDataset, params: URAlgorithmParams)
  extends Algorithm[GenericQuery, GenericQueryResult]
    with LambdaAlgorithm[UREvent]
    with SparkMongoSupport
    with JsonParser {


  private var sparkContext: Validated[ValidateError, SparkContext] = _

  case class BoostableCorrelators(actionName: String, itemIDs: Seq[String], boost: Option[Float] = None) {
    def toFilterCorrelators: FilterCorrelators = {
      FilterCorrelators(actionName, itemIDs)
    }
  }
  case class FilterCorrelators(actionName: String, itemIDs: Seq[String])
  case class ExclusionFields(propertyName: String, values: Seq[String])

  // internal settings, these are setup from init and may be changed by a `harness update <engine-id>` command
  private var recsModel: String = _
  private var userBias: Float = _
  private var itemBias: Float = _
  private var maxQueryEvents: Int = _
  private var indicatorParams: Map[String, DefaultIndicatorParams] = _
  private var limit: Int = _
  private var modelEventNames: Seq[String] = _
  private var blacklistEvents: Seq[String] = _
  private var returnSelf: Boolean = _
  private var fields: Seq[Field] = _
  private var randomSeed: Int = _
  private var numESWriteConnections: Option[Int] = _
  private var maxCorrelatorsPerEventType: Int = _
  private var maxEventsPerEventType: Int = _
  private var rankingsParams: Seq[RankingParams] = _
  private var rankingFieldNames: Seq[String] = _
  private var dateNames: Seq[String] = _

  // setting that cannot change with config
  val esIndex = dataset.getItemsDbName
  val esType = dataset.getItemsCollectionName

  def initSettings(params: URAlgorithmParams): Validated[ValidateError, String] = {
    var err: Validated[ValidateError, String] = Valid(jsonComment("URAlgorithm initialized"))

    recsModel = params.recsModel.getOrElse(DefaultURAlgoParams.RecsModel)
    //val eventNames: Seq[String] = params.eventNames

    userBias = params.userBias.getOrElse(1f)
    itemBias = params.itemBias.getOrElse(1f)

    // get max total user history events for the ES query
    maxQueryEvents = if (params.indicators.isEmpty) {
      params.maxQueryEvents.getOrElse(DefaultURAlgoParams.MaxQueryEvents)
    } else { // using the indicator method of setting query events
      params.indicators.get.foldLeft[Int](0) { (previous, indicator) =>
        previous + indicator.maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxQueryEvents)
      } * 10
      // this assumes one event doesn't happen more than 10 times more often than another
      // not ideal but avoids one query to the event store per event type
    }

    if (params.eventNames.nonEmpty) { // using eventNames shortcut
      indicatorParams = params.eventNames.get.map { eventName =>
        eventName -> DefaultIndicatorParams()
      }.toMap
    } else if (params.indicators.nonEmpty) { // using indicators for fined tuned control
      indicatorParams = params.indicators.get.map { indicatorParams =>
        indicatorParams.name -> DefaultIndicatorParams(
          maxItemsPerUser = indicatorParams.maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
          maxCorrelatorsPerItem = indicatorParams.maxCorrelatorsPerItem.getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
          minLLR = indicatorParams.minLLR)
      }.toMap
    } else {
      logger.error("Must have either eventNames or indicators in algorithm parameters, which are: " +
        s"$params")
      err = Invalid(MissingParams(jsonComment("Must have either eventNames or indicators in algorithm parameters, which are: " +
        s"$params")))
    }

    // continue validating if all is ok so far
    err.andThen { isOK =>
      limit = params.num.getOrElse(DefaultURAlgoParams.NumResults)

      modelEventNames = if (params.indicators.isEmpty) { //already know from above that one collection has names
        params.eventNames.get
      } else {
        params.indicators.get.map(_.name)
      }

      blacklistEvents = params.blacklistEvents.getOrElse(Seq(modelEventNames.head)) // empty Seq[String] means no blacklist
      returnSelf = params.returnSelf.getOrElse(DefaultURAlgoParams.ReturnSelf)
      fields = params.fields.getOrElse(Seq.empty[Field])

      randomSeed = params.seed.getOrElse(System.currentTimeMillis()).toInt

      numESWriteConnections = params.numESWriteConnections

      maxCorrelatorsPerEventType = params.maxCorrelatorsPerEventType
        .getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType)
      maxEventsPerEventType = params.maxEventsPerEventType
        .getOrElse(DefaultURAlgoParams.MaxEventsPerEventType)

      // Unique by 'type' ranking params, if collision get first.
      rankingsParams = params.rankings.getOrElse(Seq(RankingParams(
        name = Some(DefaultURAlgoParams.BackfillFieldName),
        `type` = Some(DefaultURAlgoParams.BackfillType),
        eventNames = Some(modelEventNames.take(1)),
        offsetDate = None,
        endDate = None,
        duration = Some(DefaultURAlgoParams.BackfillDuration)))).groupBy(_.`type`).map(_._2.head).toSeq

      rankingFieldNames = rankingsParams map { rankingParams =>
        val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
        val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
        rankingFieldName
      }

      dateNames = Seq(
        params.dateName,
        params.availableDateName,
        params.expireDateName).collect { case Some(date) => date } distinct

      drawInfo("URAlgorithm initialization parameters including \"defaults\"", Seq(
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("ES index name:", esIndex),
        ("ES type name:", esType),
        ("RecsModel:", recsModel),
        ("Event names:", modelEventNames),
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("Random seed:", randomSeed),
        ("MaxCorrelatorsPerEventType:", maxCorrelatorsPerEventType),
        ("MaxEventsPerEventType:", maxEventsPerEventType),
        ("BlacklistEvents:", blacklistEvents),
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("User bias:", userBias),
        ("Item bias:", itemBias),
        ("Max query events:", maxQueryEvents),
        ("Limit:", limit),
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("Rankings:", "")) ++ rankingsParams.map(x => (x.`type`.get, x.name)))

      Valid(isOK)
    }
  }


    /** Be careful to call super.init(...) here to properly make some Engine values available in scope */
  override def init(engine: Engine): Validated[ValidateError, String] = {
    super.init(engine).andThen { _ =>
      parseAndValidate[URAlgorithmParams](
        initParams,
        errorMsg = s"Error in the Algorithm part of the JSON config for engineId: $engineId, which is: " +
          s"$initParams",
        transform = _ \ "algorithm").andThen { params =>
        // Setup defaults for various params that are not set in the JSON config

        initSettings(params)
      }
    }
  }

  override def destroy(): Unit = {
  }

  override def input(datum: UREvent): Validated[ValidateError, String] = {
    logger.info("Some events may cause the UR to immediately modify the model, like property change events." +
      " This is where that will be done")
    // This deals with real-time model changes.
    datum.event match {
      // Here is where you process by reserved events which may modify the model in real-time
      case "$set" =>
        // set the property of an item in the model using the ESClient
        logger.info("Set the property of an item in the model")
        logger.warn("Not implemented!")
        if (datum.entityType == "item") {
          // set the new properties in the input DAO and in the Model item
          val event = dataset.getItemsDao.findOneById(datum.entityId).getOrElse(ItemProperties(datum.event, Map.empty
          ))
          val newProps = event.properties ++ datum.properties.getOrElse(Map.empty)
          dataset.getItemsDao.saveOneById(event._id, event.copy(properties = newProps))

          // todo: now saveOneById to the ES model also
          // dataset.itemsDAO.saveOneById(event._id, event.copy(properties = newProps))
          Valid("")
        } else Invalid(WrongParams(jsonComment("Using $set on anything but \"targetEntityType\": \"item\" is not supported")))
      case "$delete" =>
        // set the property of an item in the model using the ESClient
        logger.info("Delete an item in the model, or Delete a model")
        logger.warn("Not implemented!")
        if (datum.entityType == "model") {
          Invalid(WrongParams(jsonComment("Using $delele on targetEntityType: model is not supported yet")))
        }  else Invalid(WrongParams(jsonComment("Using $delete on anything but targetEntityType: user or model is not supported")))

      case _ =>
      // already processed by the dataset, only model changing event processed here
        Valid(jsonComment("Input event processed."))
    }
  }

  override def train(): Validated[ValidateError, String] = {
     /*
    sparkContext = createSparkContext(
      appName = dataset.engineId,
      dbName = dataset.dbName,
      collection = dataset.collection,
      config = initParams)
    */

    logger.debug(s"Starting train $this with spark $sparkContext")

    /*
    sparkContext.andThen { sc =>

      val rdd = sc.makeRDD((1 to 10000).toSeq))
      val result = rdd.ma

      Valid("URAlgorithm model creation queued for processing on the Spark cluster")
    }
    */

    val jobDescription = JobManager.addJob(engineId)
    SparkContextSupport.getSparkContext(initParams, engineId, jobDescription).map { sc => // or implicit sc =>
    //sparkContext.andThen { implicit sc =>

      val s = 1 to 10000
      val rdd = sc.parallelize(s)
      val seq = rdd.collect()
      // val rdd = createRdd(sc)
      // val result = sc.runJob(rdd, myTrainFunction)
      val result = rdd.fold(0) { (last, current) => last + current }
      //val r2 = rdd.collect() // forces job to complete
      //sc.stop() // always stop or it will be active forever

      logger.info(
        s"""
           |URAlgorithm.train
           |  Status for appname: ${sc.appName}
           |  Run on master: ${sc.master}
           |  Result: $result
           |  Completed asynchronously
        """.stripMargin
      )

    }

    Valid(jsonComment("Started train Job on Spark"))
  }

  def query(query: GenericQuery): GenericQueryResult = {
    GenericQueryResult()
  }

}

object URAlgorithm extends JsonParser {

  def apply(engine: UREngine, initParams: String, dataset: URDataset): URAlgorithm = {

    val params = parseAndValidate[URAlgorithmParams](initParams, transform = _ \ "algorithm").andThen { params =>
      Valid(true, params)
    }.map(_._2).getOrElse(null.asInstanceOf[URAlgorithmParams])
    new URAlgorithm(engine, initParams, dataset, params)
  }

  /** Available value for algorithm param "RecsModel" */
  object RecsModels { // todo: replace this with rankings
    val All = "all"
    val CF = "collabFiltering"
    val BF = "backfill"
    override def toString: String = s"$All, $CF, $BF"
  }

  /** Setting the option in the params case class doesn't work as expected when the param is missing from
    *  engine.json so set these for use in the algorithm when they are not present in the engine.json
    */
  object DefaultURAlgoParams {
    val ModelType = "items"
    val MaxEventsPerEventType = 500
    val NumResults = 20
    val MaxCorrelatorsPerEventType = 50
    val MaxQueryEvents = 100 // default number of user history events to use in recs query

    val ExpireDateName = "expireDate" // default name for the expire date property of an item
    val AvailableDateName = "availableDate" //defualt name for and item's available after date
    val DateName = "date" // when using a date range in the query this is the name of the item's date
    val RecsModel = RecsModels.All // use CF + backfill
    //val RankingParams = RankingParams()
    val BackfillFieldName = RankingFieldName.PopRank
    val BackfillType = RankingType.Popular
    val BackfillDuration = "3650 days" // for all time

    val ReturnSelf = false
    val NumESWriteConnections: Option[Int] = None
  }

  case class RankingParams(
      name: Option[String] = None,
      `type`: Option[String] = None, // See [[com.actionml.BackfillType]]
      eventNames: Option[Seq[String]] = None, // None means use the algo eventNames findMany, otherwise a findMany of events
      offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
      // recent date for events going back by from the more recent offsetDate - duration
      endDate: Option[String] = None,
      duration: Option[String] = None) { // duration worth of events to use in calculation of backfill
    override def toString: String = {
      s"""
         |_id: $name,
         |type: ${`type`},
         |eventNames: $eventNames,
         |offsetDate: $offsetDate,
         |endDate: $endDate,
         |duration: $duration
         |""".stripMargin
    }
  }

  case class DefaultIndicatorParams(
      maxItemsPerUser: Int = DefaultURAlgoParams.MaxQueryEvents, // defaults to maxEventsPerEventType
      maxCorrelatorsPerItem: Int = DefaultURAlgoParams.MaxCorrelatorsPerEventType,
      // defaults to maxCorrelatorsPerEventType
      minLLR: Option[Double] = None) // defaults to none, takes precendence over maxCorrelatorsPerItem

  case class IndicatorParams(
      name: String, // must match one in eventNames
      maxItemsPerUser: Option[Int], // defaults to maxEventsPerEventType
      maxCorrelatorsPerItem: Option[Int], // defaults to maxCorrelatorsPerEventType
      minLLR: Option[Double]) // defaults to none, takes precendence over maxCorrelatorsPerItem

  case class URAlgorithmParams(
      indexName: Option[String], // can optionally be used to specify the elasticsearch index name
      typeName: Option[String], // can optionally be used to specify the elasticsearch type name
      recsModel: Option[String] = None, // "all", "collabFiltering", "backfill"
      eventNames: Option[Seq[String]], // names used to ID all user actions
      blacklistEvents: Option[Seq[String]] = None, // None means use the primary event, empty array means no filter
      // number of events in user-based recs query
      maxQueryEvents: Option[Int] = None,
      maxEventsPerEventType: Option[Int] = None,
      maxCorrelatorsPerEventType: Option[Int] = None,
      num: Option[Int] = None, // default max # of recs requested
      userBias: Option[Float] = None, // will cause the default search engine boost of 1.0
      itemBias: Option[Float] = None, // will cause the default search engine boost of 1.0
      returnSelf: Option[Boolean] = None, // query building logic defaults this to false
      fields: Option[Seq[Field]] = None, //defaults to no fields
      // leave out for default or popular
      rankings: Option[Seq[RankingParams]] = None,
      // name of date property field for when the item is available
      availableDateName: Option[String] = None,
      // name of date property field for when an item is no longer available
      expireDateName: Option[String] = None,
      // used as the subject of a dateRange in queries, specifies the name of the item property
      dateName: Option[String] = None,
      indicators: Option[List[IndicatorParams]] = None, // control params per matrix pair
      seed: Option[Long] = None, // seed is not used presently
      numESWriteConnections: Option[Int] = None) // hint about how to coalesce partitions so we don't overload ES when
  // writing the model. The rule of thumb is (numberOfNodesHostingPrimaries * bulkRequestQueueLength) * 0.75
  // for ES 1.7 bulk queue is defaulted to 50
  //  extends Params //fixed default make it reproducible unless supplied

  /** This file contains case classes that are used with reflection to specify how query and config
    * JSON is to be parsed. the Query case class, for instance defines the way a JSON query is to be
    * formed. The same for param case classes.
    */

  /** The Query spec with optional values. The only hard rule is that there must be either a user or
    *  an item id. All other values are optional.
    */
  case class Query(
      user: Option[String] = None, // must be a user or item id
      userBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      item: Option[String] = None, // must be a user or item id
      itemBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      itemSet: Option[List[String]] = None, // item-set query, shpping cart for instance.
      itemSetBias: Option[Float] = None, // default: whatever is in algorithm params or 1
      fields: Option[List[Field]] = None, // default: whatever is in algorithm params or None
      currentDate: Option[String] = None, // if used will override dateRange filter, currentDate must lie between the item's
      // expireDateName value and availableDateName value, all are ISO 8601 dates
      dateRange: Option[DateRange] = None, // optional before and after filter applied to a date field
      blacklistItems: Option[List[String]] = None, // default: whatever is in algorithm params or None
      returnSelf: Option[Boolean] = None, // means for an item query should the item itself be returned, defaults
      // to what is in the algorithm params or false
      num: Option[Int] = None, // default: whatever is in algorithm params, which itself has a default--probably 20
      from: Option[Int] = None, // paginate from this position return "num"
      eventNames: Option[List[String]], // names used to ID all user actions
      withRanks: Option[Boolean] = None) // Add to ItemScore rank fields values, default false
    extends Serializable

  /** Used to specify how Fields are represented in engine.json */
  case class Field( // no optional values for fields, whne specified
      name: String, // name of metadata field
      values: Seq[String], // fields can have multiple values like tags of a single value as when using hierarchical
      // taxonomies
      bias: Float) // any positive value is a boost, negative is an inclusion filter, 0 is an exclusion filter
    extends Serializable

  /** Used to specify the date range for a query */
  case class DateRange(
      name: String, // name of item property for the date comparison
      before: Option[String], // empty strings means no filter
      after: Option[String]) // both empty should be ignored
    extends Serializable

  /** results of a URAlgoritm.predict */
  case class PredictedResult(
      itemScores: Array[ItemScore])
    extends Serializable

  case class ItemScore(
      item: String, // item id
      score: Double, // used to rank only, score returned from the search engine
      ranks: Option[Map[String, Double]] = None)
    extends Serializable
}

