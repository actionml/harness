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

import java.io.Serializable

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.engine._
import com.actionml.core.jobs.{JobDescription, JobManager}
import com.actionml.core.model.{Comment, Response}
import com.actionml.core.search.elasticsearch.ElasticSearchClient
import com.actionml.core.search.{Hit, Matcher, SearchQuery}
import com.actionml.core.spark.SparkContextSupport
import com.actionml.core.store.sparkmongo.syntax._
import com.actionml.core.store.{DAO, DaoQuery}
import com.actionml.core.validate.{JsonSupport, MissingParams, ValidateError, WrongParams}
import com.actionml.engines.urnavhinting.URNavHintingAlgorithm.URAlgorithmParams
import com.actionml.engines.urnavhinting.URNavHintingEngine.{URNavHintingEvent, URNavHintingQuery, URNavHintingQueryResult}
import org.apache.mahout.math.cf.{DownsamplableCrossOccurrenceDataset, SimilarityAnalysis}
import org.apache.mahout.math.indexeddataset.IndexedDataset
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration


/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class URNavHintingAlgorithm private (
        engine: URNavHintingEngine,
        initParams: String,
        dataset: URNavHintingDataset,
        params: URAlgorithmParams,
        eventsDao: DAO[URNavHintingEvent])
  extends Algorithm[URNavHintingQuery, URNavHintingQueryResult]
  with LambdaAlgorithm[URNavHintingEvent]
  with JsonSupport {

  import URNavHintingAlgorithm._

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
  private var es: ElasticSearchClient = _
  private var indicators: Option[List[IndicatorParams]] = None
  private var seed: Option[Long] = None

  // setting that cannot change with config
  val esIndex = engineId
  val esType = "items"

  def initSettings(params: URAlgorithmParams): Validated[ValidateError, Response] = {
    var err: Validated[ValidateError, String] = Valid(jsonComment("URNavHintingAlgorithm initialized"))

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

    indicators = params.indicators
    seed = params.seed

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
      logger.error("Must have either \"indicatorNames\" or \"indicators\" in algorithm parameters, which are: " +
        s"$params")
      err = Invalid(MissingParams(jsonComment("Must have either indicatorNames or indicators in algorithm parameters, which are: " +
        s"$params")))
    }


    // continue validating if all is ok so far
    err.andThen { isOK =>
      limit = params.num.getOrElse(DefaultURAlgoParams.NumResults)

      modelEventNames = if (params.indicators.isEmpty) { //already know from above that one collection has names
        params.eventNames.get
      } else {
        params.indicators.get.map(_.name)
      }.toSeq

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

      es = ElasticSearchClient(engineId)

      drawInfo("URNavHintingAlgorithm initialization parameters including \"defaults\"", Seq(
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

      Valid(Comment(isOK))
    }
  }


    /** Be careful to call super.init(...) here to properly make some Engine values available in scope */
  override def init(engine: Engine): Validated[ValidateError, Response] = {
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
    // todo: delete the model, only the algorithm knows where it is
    es.deleteIndex()
  }

  override def input(datum: URNavHintingEvent): Validated[ValidateError, Response] = {
    // This deals with real-time model changes, if any are implemented
    // todo: none do anything for the PoC so all return errors
    datum.event match {
      // Here is where you process by reserved events which may modify the model in real-time
      case "$set" =>
        Invalid(WrongParams(jsonComment("Using $set not supported")))
      /*case "$delete" =>
        datum.entityType match {
          case "model" =>
            logger.info(s"Deleted data for model: ${datum.entityId}, retrain to get it reflected in new queries")
            // Todo: remove the model data from Elasticsearch? Lots of work, may require a Job
            Valid(jsonComment("Using $delele on entityType: model is not supported yet"))
          case _ =>
            logger.warn(s"Deleting unknown entityType is not supported.")
            Invalid(WrongParams(jsonComment(s"Deleting unknown entityType is not supported.")))
        }
      */
      case _ =>
      // already processed by the dataset, only model changing event processed here
        Valid(Comment("URNavHinting input processed"))
    }
  }

  override def train(): Validated[ValidateError, Response] = {
    val (f, jobDescription) = SparkContextSupport.getSparkContext(initParams, engineId, kryoClasses = Array(classOf[URNavHintingEvent]))
    f.map { implicit sc =>
      val eventsRdd = eventsDao.readRdd[URNavHintingEvent](MongoStorageHelper.codecs)

      // todo: this should work but not tested and not used in any case
      /*
      val fieldsRdd = readRdd[ItemProperties](sc, MongoStorageHelper.codecs, Some(dataset.getItemsDbName), Some(dataset.getItemsCollectionName)).map { itemProps =>
        (itemProps._id, itemProps.properties)
      }
      */

      val trainingData = URNavHintingPreparator.mkTraining(modelEventNames, eventsRdd)

      //val collectedActions = trainingData.actions.map { case (en, rdd) => (en, rdd.collect()) }
      //val collectedFields = trainingData.fieldsRDD.collect()
      val data = URNavHintingPreparator.prepareData(trainingData)

      val model = recsModel match {
        case RecsModels.All => calcAll(data, eventsRdd)
        case RecsModels.CF => calcAll(data, eventsRdd, calcPopular = false)
        // todo: no support for pure popular model
        // case RecsModels.BF  => calcPop(data)(sc)
        // error, throw an exception
        case unknownRecsModel => // todo: this better not be fatal. we need a way to disallow fatal excpetions
          // from Spark or any part fo the UR. These were allowed in PIO--not here!
          throw new IllegalArgumentException(
            s"""
               |Bad algorithm param recsModel=[$unknownRecsModel] in engine definition params, possibly a bad json value.
               |Use one of the available parameter values ($recsModel).""".stripMargin)
      }

      //val data = getIndicators(modelEventNames, eventsRdd)

      logger.info("======================================== Contents of Indicators ========================================")
      data.actions.foreach { case (name, id) =>
        val ids = id.asInstanceOf[IndexedDatasetSpark]
        logger.info(s"Event name: $name")
        logger.info(s"Num users/rows = ${ids.matrix.nrow}")
        logger.info(s"Num items/columns = ${ids.matrix.ncol}")
        logger.info(s"User dictionary: ${ids.rowIDs.toMap.keySet}")
        logger.info(s"Item dictionary: ${ids.columnIDs.toMap.keySet}")
      }
      logger.info("======================================== done ========================================")

      // todo: for now ignore properties and only calc popularity, then save to ES
      calcAll(data, eventsRdd).save(dateNames, esIndex, esType, numESWriteConnections)

      //sc.stop() // no more use of sc will be tolerated ;-)
    }

    // todo: EsClient.close() can't be done because the Spark driver might be using it unless its done in the Furute
    logger.debug(s"Starting train $this with spark")
    Valid(Comment("Started train Job on Spark"))
  }

  /*
  def getIndicators(
    modelEventNames: Seq[String],
    eventsRdd: RDD[URNavHintingEvent])
    (implicit sc: SparkContext): PreparedData = {
    URNavHintingPreparator.prepareData(modelEventNames, eventsRdd)
  }
  */

  /** Calculates recs model as well as popularity model */
  def calcAll(
    data: PreparedData,
    eventsRdd: RDD[URNavHintingEvent],
    calcPopular: Boolean = true)(implicit sc: SparkContext): URNavHintingModel = {

    /*logger.info("Indicators read now creating correlators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.actions.map(_._2).toArray,
      ap.seed.getOrElse(System.currentTimeMillis()).toInt)
      .map(_.asInstanceOf[IndexedDatasetSpark])
    */

    //val in = data.actions.map { case ( en, ids) => ids.asInstanceOf[IndexedDatasetSpark].toStringMapRDD(en).collect()}

    val convertedItems = data.actions.filter { case (en, ids) => en == modelEventNames.head}
      .head._2.columnIDs.toMap.keySet.toSeq

    logger.info("Actions read now creating correlators")
    val cooccurrenceIDSs = if (indicators.isEmpty) { // using one global set of algo params
      SimilarityAnalysis.cooccurrencesIDSs(
        data.actions.map(_._2).toArray,
        randomSeed = seed.getOrElse(System.currentTimeMillis()).toInt,
        maxInterestingItemsPerThing = maxCorrelatorsPerEventType,
        maxNumInteractions = maxEventsPerEventType)
        .map(_.asInstanceOf[IndexedDatasetSpark])
    } else { // using params per matrix pair, these take the place of eventNames, maxCorrelatorsPerEventType,
      // and maxEventsPerEventType!
      val iDs = data.actions.map(_._2).toSeq
      val datasets = iDs.zipWithIndex.map {
        case (iD, i) =>
          new DownsamplableCrossOccurrenceDataset(
            iD,
            indicators.get(i).maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
            indicators.get(i).maxCorrelatorsPerItem.getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
            indicators.get(i).minLLR)
      }.toList

      SimilarityAnalysis.crossOccurrenceDownsampled(
        datasets,
        seed.getOrElse(System.currentTimeMillis()).toInt)
        .map(_.asInstanceOf[IndexedDatasetSpark])
    }

    //val collectedIdss = cooccurrenceIDSs.map(_.toStringMapRDD("anon").collect())

    val cooccurrenceCorrelators = cooccurrenceIDSs.zip(data.actions.map(_._1)).map(_.swap) //add back the actionNames

    val propertiesRDD: RDD[(ItemID, PropertyMap)] = if (calcPopular) {
      val ranksRdd = getRanksRDD(data.fieldsRdd, eventsRdd, convertedItems)
      //val colledtedFileds = data.fieldsRdd.collect()
      //colledtedFileds.foreach { f => logger.info(s"$f") }
      //val collectedRanks = ranksRdd.collect()
      //collectedRanks.foreach { f => logger.info(s"$f") }
      //logger.info("About to add properties and ranking")
      data.fieldsRdd.fullOuterJoin(ranksRdd).map {
        case (item, (Some(fieldsPropMap), Some(rankPropMap))) => item -> (fieldsPropMap ++ rankPropMap)
        case (item, (Some(fieldsPropMap), None))              => item -> fieldsPropMap
        case (item, (None, Some(rankPropMap)))                => item -> rankPropMap
        case (item, _)                                        => item -> Map.empty
      }
    } else {
      sc.emptyRDD
    }

    //val collectedProps = propertiesRDD.collect()

    logger.info("Correlators created now putting into URModel")
    new URNavHintingModel(
      coocurrenceMatrices = cooccurrenceCorrelators,
      propertiesRDDs = Seq(propertiesRDD),
      typeMappings = getMappings)(sc, es)
  }



  def query(query: URNavHintingQuery): Future[URNavHintingQueryResult] = {
    // todo: need to hav an API to see if the alias and index exist. If not then send a friendly error message
    // like "you forgot to train"
    // todo: order by date
    import DaoQuery.syntax._
    val unconvertedHist = dataset.getActiveJourneysDao.findMany(limit= maxQueryEvents * 100)("entityId" === query.user)
    val convertedHist = dataset.getIndicatorsDao.findMany(limit= maxQueryEvents * 100)("entityId" === query.user)
    val userEvents = modelEventNames.map { n =>
      (n,
        (unconvertedHist.filter(_.event == n).map(_.targetEntityId.get).toSeq ++
        convertedHist.filter(_.event == n).map(_.targetEntityId.get).toSeq).distinct
      )
    }
    val shouldMatchers = userEvents.map { case(n, hist) => Matcher(n, hist) }
    val mustMatcher = Matcher("values", query.eligibleNavIds)
    val esQuery = SearchQuery(
      should = shouldMatchers,
      must = Seq(mustMatcher),
      sortBy = "popRank",
      size = limit
    )
    logger.info(s"Sending query: $esQuery")
    val esResult = es.search(esQuery).map(_.map { hit => (hit.id, hit.score.toDouble)})
//    URNavHintingQueryResult(esResult)
    ???
  }

  /** Calculate all fields and items needed for ranking.
    *
    *  @param fieldsRDD all items with their fields
    *  @param sc the current Spark context
    *  @return
    */
  def getRanksRDD(
    fieldsRDD: RDD[(ItemID, PropertyMap)],
    eventsRdd: RDD[URNavHintingEvent],
    convertedItems: Seq[String])
    (implicit sc: SparkContext): RDD[(ItemID, PropertyMap)] = {

    val popModel = PopModel(fieldsRDD)
    val rankRDDs: Seq[(String, RDD[(ItemID, Double)])] = rankingsParams map { rankingParams =>
      val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
      val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
      val durationAsString = rankingParams.duration.getOrElse(DefaultURAlgoParams.BackfillDuration)
      val duration = Duration(durationAsString).toSeconds.toInt
      val backfillEvents = rankingParams.eventNames.getOrElse(modelEventNames.take(1))
      val offsetDate = rankingParams.offsetDate
      val rankRdd = popModel.calc(eventsRdd, backfillEvents, duration, offsetDate)
      rankingFieldName -> rankRdd
    }
    //    logger.debug(s"RankRDDs[${rankRDDs.size}]\n${rankRDDs.map(_._1).mkString(", ")}\n${rankRDDs.map(_._2.take(25).mkString("\n")).mkString("\n\n")}")
    rankRDDs
      .foldLeft[RDD[(ItemID, PropertyMap)]](sc.emptyRDD) {
      case (leftRdd, (fieldName, rightRdd)) =>
        leftRdd.fullOuterJoin(rightRdd).map {
          case (itemId, (Some(propMap), Some(rank))) => itemId -> (propMap + (fieldName -> rank))
          case (itemId, (Some(propMap), None))       => itemId -> propMap
          case (itemId, (None, Some(rank)))          => itemId -> Map(fieldName -> rank)
          case (itemId, _)                           => itemId -> Map.empty
        }
    }.filter { case (itemId, props) => convertedItems.contains(itemId) }
  }

  def getMappings: Map[String, (String, Boolean)] = {
    val mappings = rankingFieldNames.map { fieldName =>
      fieldName -> ("float", false)
    }.toMap ++ // create mappings for correlators, where the Boolean says to not use norms
      modelEventNames.map { correlator =>
        correlator -> ("keyword", true) // use norms with correlators to get closer to cosine similarity.
      }.toMap ++
      dateNames.map { dateName =>
        dateName -> ("date", false) // map dates to be interpreted as dates
      }
    logger.info(s"Index mappings for the Elasticsearch URNavHintingModel: $mappings")
    mappings
  }

}

object URNavHintingAlgorithm extends JsonSupport {

  def apply(engine: URNavHintingEngine, initParams: String, dataset: URNavHintingDataset, eventsDao: DAO[URNavHintingEvent]): URNavHintingAlgorithm = {
    val params = parseAndValidate[URAlgorithmParams](initParams, transform = _ \ "algorithm").andThen { params =>
      Valid(true, params)
    }.map(_._2).getOrElse(null.asInstanceOf[URAlgorithmParams])
    new URNavHintingAlgorithm(engine, initParams, dataset, params, eventsDao)
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
         |indicatorNames: $eventNames,
         |offsetDate: $offsetDate,
         |endDate: $endDate,
         |duration: $duration
      """.stripMargin
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

  case class PreparedData(
      actions: Seq[(String, IndexedDataset)],
      fieldsRdd: RDD[(String, Map[String, Any])]) extends Serializable

  case class TrainingData(
    actions: Seq[(String, RDD[(String, String)])],// indicator name, RDD[user-id, item-id]
    fieldsRDD: RDD[(String, Map[String, Any])], // RDD[ item-id, Map[String, Any] or property map
    minEventsPerUser: Option[Int] = Some(1)) extends Serializable

}

