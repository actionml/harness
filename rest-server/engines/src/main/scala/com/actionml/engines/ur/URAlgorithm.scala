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

import java.io.Serializable
import java.util.Date

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.engine._
import com.actionml.core.jobs.{JobDescription, JobManager}
import com.actionml.core.model.{Comment, Response}
import com.actionml.core.search.elasticsearch.ElasticSearchClient
import com.actionml.core.search.{Filter, Hit, Matcher, SearchQuery}
import com.actionml.core.spark.{LivyJobServerSupport, SparkContextSupport, SparkJobServerSupport}
import com.actionml.core.store.sparkmongo.syntax._
import com.actionml.core.store.{DAO, DaoQuery, OrderBy, Ordering}
import com.actionml.core.validate.{JsonSupport, ValidRequestExecutionError, ValidateError, WrongParams}
import com.actionml.engines.ur.URAlgorithm.URAlgorithmParams
import com.actionml.engines.ur.UREngine.{ItemScore, Rule, UREvent, URItemProperties, URQuery, URQueryResult}
import org.apache.mahout.math.cf.{DownsamplableCrossOccurrenceDataset, SimilarityAnalysis}
import org.apache.mahout.math.indexeddataset.IndexedDataset
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal


/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class URAlgorithm private (
    engine: UREngine,
    initParams: String,
    params: URAlgorithmParams,
    eventsDao: DAO[UREvent],
    itemsDao: DAO[URItemProperties])
  extends Algorithm[URQuery, URQueryResult]
  with LambdaAlgorithm[UREvent]
  with JsonSupport {

  import URAlgorithm._

  // internal settings, these are setup from init and may be changed by a `harness update <engine-id>` command
  private var recsModel: String = _
  private var userBias: Float = _
  private var itemBias: Float = _
  private var itemSetBias: Float = _
  private var maxQueryEvents: Int = _
  private var indicatorParams: Seq[IndicatorParams] = _
  private var limit: Int = _
  private var modelEventNames: Seq[String] = _
  private var blacklistEvents: Seq[String] = _
  private var returnSelf: Boolean = _
  private var fields: Seq[Rule] = _
  private var randomSeed: Int = _
  private var numESWriteConnections: Option[Int] = _
  private var maxCorrelatorsPerEventType: Int = _
  private var maxEventsPerEventType: Int = _
  private var rankingsParams: Seq[RankingParams] = _
  private var rankingFieldNames: Seq[String] = _
  private var dateNames: Set[String] = _
  private var es: ElasticSearchClient = _
  private var indicators: Seq[IndicatorParams] = _
  private var seed: Option[Long] = None
  private var rules: Option[Seq[Rule]] = _
  private var availableDateName: Option[String] = _
  private var expireDateName: Option[String] = _
  private var itemDateName: Option[String] = _
  private var queryEventNames: Map[String, String] = _
  private var indicatorsMap: Map[String, IndicatorParams] = _


  // setting that cannot change with config
  val esIndex = engineId
  val esType = "items"

  def initSettings(params: URAlgorithmParams): Validated[ValidateError, Response] = {
    var err: Validated[ValidateError, Response] = Valid(Comment("URAlgorithm initialized"))

    recsModel = params.recsModel.getOrElse(DefaultURAlgoParams.RecsModel)
    //val indicatorParams: Seq[String] = params.indicatorParams

    userBias = params.userBias.getOrElse(1f)
    itemBias = params.itemBias.getOrElse(1f)

    // get max total user history events for the ES query
    // this assumes one event doesn't happen more than 10 times more often than another
    // not ideal but avoids one query to the event store per event type
    maxQueryEvents = params.indicators.foldLeft[Int](0) { (previous, indicator) =>
        previous + (indicator.maxIndicatorsPerQuery.getOrElse(DefaultURAlgoParams.MaxQueryEvents) * DefaultURAlgoParams.FudgeFactor)
    }


    indicators = params.indicators

    seed = params.seed
    rules = params.rules
    availableDateName = params.availableDateName
    expireDateName = params.expireDateName
    itemDateName = params.dateName
    indicatorParams = params.indicators
    indicatorsMap = indicators.map{ i =>
      (i.name, i)
    }.toMap

    // continue validating if all is ok so far
    err.andThen { isOK =>
      // create a Map of alias -> indicator name or indicator name -> indicator name if no aliases
      queryEventNames = indicatorParams.flatMap { i =>
        val aliases = i.aliases.getOrElse(Seq(i.name))
        if(i.name == aliases.head && aliases.size == 1) {
          Map(i.name -> i.name)
        } else {
          aliases.map(_ -> i.name)
        }.toMap
      }.toMap

      logger.info(s"Engine-id: ${engineId}. Events to alias mapping: ${queryEventNames}")
      limit = params.num.getOrElse(DefaultURAlgoParams.NumResults)

      modelEventNames = params.indicators.map(_.name)

      blacklistEvents = params.blacklistIndicators.getOrElse(Seq(modelEventNames.head)) // empty Seq[String] means no blacklist
      returnSelf = params.returnSelf.getOrElse(DefaultURAlgoParams.ReturnSelf)
      fields = params.rules.getOrElse(Seq.empty[Rule])

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
        indicatorNames = Some(modelEventNames.take(1)),
        offsetDate = None,
        endDate = None,
        duration = Some(DefaultURAlgoParams.BackfillDuration)))).groupBy(_.`type`).map(_._2.head).toSeq

      rankingFieldNames = rankingsParams map { rankingParams =>
        val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
        val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
        rankingFieldName
      }

      dateNames = Set(
        params.dateName,
        params.availableDateName,
        params.expireDateName).flatten


      es = ElasticSearchClient(engineId)

      drawInfo("URAlgorithm initialization parameters including \"defaults\"", Seq(
        ("════════════════════════════════════════", "══════════════════════════════════════"),
        ("ES index name:", esIndex),
        ("ES type name:", esType),
        ("RecsModel:", recsModel),
        //("Event names:", modelEventNames),
        ("Indicators:", indicatorParams),
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

  override def input(datum: UREvent): Validated[ValidateError, Response] = {
    // This deals with real-time model changes, if any are implemented
    // todo: none do anything for the PoC so all return errors
    datum.event match {
      // Here is where you process by reserved events which may modify the model in real-time
      //case "$set" => // todo: modify the model, if there is one
        // Invalid(WrongParams(jsonComment("Using $set not supported")))
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
        Valid(Comment("UR input processed"))
    }
  }

  override def train(): Validated[ValidateError, Response] = {
    val (f, jobDescription) = SparkContextSupport.getSparkContext(initParams, engineId, kryoClasses = Array(classOf[UREvent]))
    f.map { implicit sc =>
      logger.info(s"Engine-id: ${engineId}. Spark context spark.submit.deployMode: ${sc.deployMode}")
      try {
        val eventsRdd = eventsDao.readRdd[UREvent](MongoStorageHelper.codecs).repartition(sc.defaultParallelism)
        val itemsRdd = itemsDao.readRdd[URItemProperties](MongoStorageHelper.codecs)

        val trainingData = URPreparator.mkTraining(indicatorParams, eventsRdd, itemsRdd)

        //val collectedActions = trainingData.indicatorRDDs.map { case (en, rdd) => (en, rdd.collect()) }
        //val collectedFields = trainingData.fieldsRDD.collect()
        val data = URPreparator.prepareData(trainingData)

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
        data.indicatorRDDs.foreach { case (name, id) =>
          val ids = id.asInstanceOf[IndexedDatasetSpark]
          logger.info(s"Engine-id: ${engineId}. Event name: $name")
          logger.info(s"Engine-id: ${engineId}. Num users/rows = ${ids.matrix.nrow}")
          logger.info(s"Engine-id: ${engineId}. Num items/columns = ${ids.matrix.ncol}")
          // do not log these in real world situations, only for small debug test data
          // logger.info(s"Engine-id: ${engineId}. User dictionary: ${ids.rowIDs.toMap.keySet}")
          // logger.info(s"Engine-id: ${engineId}. Item dictionary: ${ids.columnIDs.toMap.keySet}")
        }
        logger.info("======================================== done ========================================")

        // todo: for now ignore properties and only calc popularity, then save to ES
        calcAll(data, eventsRdd).save(dateNames, esIndex, esType, numESWriteConnections)
        JobManager.finishJob(jobDescription.jobId)
      } catch {
        case NonFatal(e) =>
          logger.error(s"Spark computation failed for engine $engineId with params {$initParams}", e)
          JobManager.markJobFailed(jobDescription.jobId)
      } finally {
        SparkContextSupport.stopAndClean(sc)
      }
    }
    logger.trace(s"Engine-id: ${engineId}. Starting train with spark")
    Valid(TrainResponse(jobDescription, "Started train Job on Spark"))
  }

  /*
  def getIndicators(
    modelEventNames: Seq[String],
    eventsRdd: RDD[UREvent])
    (implicit sc: SparkContext): PreparedData = {
    URPreparator.prepareData(modelEventNames, eventsRdd)
  }
  */

  /** Calculates recs model as well as popularity model */
  def calcAll(
    data: PreparedData,
    eventsRdd: RDD[UREvent],
    calcPopular: Boolean = true)(implicit sc: SparkContext): URModel = {

    /*logger.info("Indicators read now creating correlators")
    val cooccurrenceIDSs = SimilarityAnalysis.cooccurrencesIDSs(
      data.indicatorRDDs.map(_._2).toArray,
      ap.seed.getOrElse(System.currentTimeMillis()).toInt)
      .map(_.asInstanceOf[IndexedDatasetSpark])
    */

    //val in = data.indicatorRDDs.map { case ( en, ids) => ids.asInstanceOf[IndexedDatasetSpark].toStringMapRDD(en).collect()}

    logger.info(s"Engine-id: ${engineId}. Indicator names: ${modelEventNames}")
    logger.info(s"Engine-id: ${engineId}. Indicator RDD names: ${data.indicatorRDDs.map { case (en, ids) => en }.mkString(",")}")
    val convertedItems = data.indicatorRDDs.filter { case (en, ids) => en == modelEventNames.head}
      .head._2.columnIDs.toMap.keySet.toSeq

    logger.trace("Engine-id: ${engineId}. Actions read now creating correlators")
    val cooccurrenceIDSs = {
      val iDs = data.indicatorRDDs.map(_._2)
      val datasets = iDs.zipWithIndex.map {
        case (iD, i) =>
          DownsamplableCrossOccurrenceDataset(
            iD,
            indicators(i).maxIndicatorsPerQuery.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
            indicators(i).maxCorrelatorsPerItem.getOrElse(DefaultURAlgoParams.MaxCorrelatorsPerEventType),
            indicators(i).minLLR)
      }.toList

      SimilarityAnalysis.crossOccurrenceDownsampled(
        datasets,
        seed.getOrElse(System.currentTimeMillis()).toInt)
        .map(_.asInstanceOf[IndexedDatasetSpark])
    }

    //val collectedIdss = cooccurrenceIDSs.map(_.toStringMapRDD("anon").collect())

    val cooccurrenceCorrelators = cooccurrenceIDSs.zip(data.indicatorRDDs.map(_._1)).map(_.swap) //add back the actionNames

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

    //val collectedProps = propertiesRDD.flatMap(_._2.keys).distinct().collect()
    val propertiesMappings = data.fieldsRdd.flatMap(_._2.keys).distinct().collect().map(_ -> ("keyword" -> true)) ++ Map("id" -> ("keyword" -> true))

    val allMappings = getMappings ++ propertiesMappings

    logger.trace(s"Engine-id: ${engineId}. Correlators created now putting into URModel")
    new URModel(
      coocurrenceMatrices = cooccurrenceCorrelators,
      propertiesRDDs = Seq(propertiesRDD),
      typeMappings = allMappings)(sc, es)
  }


  override def query(query: URQuery): Future[URQueryResult] = {
    // todo: need to have an API to see if the alias and index exist. If not then send a friendly error message
    // like "you forgot to train"
    /*
        queryEventNames = query.indicatorParams.getOrElse(modelEventNames) // indicatorParams in query take precedence

    val (queryStr, blacklist) = buildQuery(ap, query, rankingFieldNames)
    // old es1 query
    // val searchHitsOpt = EsClient.search(queryStr, esIndex, queryEventNames)
    val searchHitsOpt = EsClient.search(queryStr, esIndex)

    val withRanks = query.withRanks.getOrElse(false)
    val predictedResults = searchHitsOpt match {
      case Some(searchHits) =>
        val hits = (searchHits \ "hits" \ "hits").extract[Seq[JValue]]
        val recs = hits.map { hit =>
          if (withRanks) {
            val source = hit \ "source"
            val ranks: Map[String, Double] = rankingsParams map { backfillParams =>
              val backfillType = backfillParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
              val backfillFieldName = backfillParams.name.getOrElse(PopModel.nameByType(backfillType))
              backfillFieldName -> (source \ backfillFieldName).extract[Double]
            } toMap

            ItemScore((hit \ "_id").extract[String], (hit \ "_score").extract[Double],
              ranks = if (ranks.nonEmpty) Some(ranks) else None)
          } else {
            ItemScore((hit \ "_id").extract[String], (hit \ "_score").extract[Double])
          }
        }.toArray
        logger.info(s"Results: ${hits.length} retrieved of a possible ${(searchHits \ "hits" \ "total").extract[Long]}")
        PredictedResult(recs)

      case _ =>
        logger.info(s"No results for query ${parse(queryStr)}")
        PredictedResult(Array.empty[ItemScore])

     */


    val modelQuery = buildModelQuery(query)
    es.search(modelQuery).map(items => URQueryResult(items.map(hit => ItemScore(hit.id, hit.score)))) // todo: optionally return rankings?
  }

  override def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    try {
      JobManager.getActiveJobDescriptions(engineId).find(_.jobId == jobId).fold[Validated[ValidateError, Response]] {
        Invalid(WrongParams(s"No jobId $jobId found"))
      } { _ =>
        Await.result(JobManager.cancelJob(engineId, jobId), 5.minutes)
        Valid(Comment(s"Job $jobId aborted successfully"))
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Engine-id: ${engineId}. Cannot abort job: $jobId abort error", e)
        Invalid(ValidRequestExecutionError(s"Cannot abort job: $jobId"))
    }
  }


  private def buildModelQuery(query: URQuery): SearchQuery = {
    logger.info(s"Engine-id: ${engineId}. Got query: \n${query}")

    val aggregatedRules = aggregateRules(rules, query.rules)

    val startPos = query.from.getOrElse(0)
    val numResults = query.num.getOrElse(limit)

    // create a list of all query correlators that can have a bias (boost or filter) attached
    val (userHistoryMatchers, userEvents) = getUserHistMatcher(query)
    val shouldMatchers =
      userHistoryMatchers ++
      getSimilarItemsMatchers(query) ++
      getItemSetMatchers(query) ++
      getBoostedRulesMatchers(aggregatedRules)

    val mustMatchers = getIncludeRulesMatchers(aggregatedRules)
    val mustNotMatchers = getExcludeRulesMatchers(aggregatedRules) ++ getBlacklistedItemsMatchers(query, userEvents)

    SearchQuery(
      sortBy = rankingsParams.head.name.getOrElse("popRank"), // todo: this should be a list of ranking rules
      should = shouldMatchers,
      must = mustMatchers,
      mustNot = mustNotMatchers,
      filters = getDateFilters(query),
      size = numResults,
      from = startPos
    )
  }

  /** Aggregates unique Rules by name, discarding config rules that are named the same as a query rule */
  private def aggregateRules(configRules: Option[Seq[Rule]] = None, queryRules: Option[Seq[Rule]] = None): Seq[Rule] = {
    val qRules = queryRules.getOrElse(Seq.empty)
    val qRuleNames = qRules.map(_.name)
    val validConfigRules = configRules.getOrElse(Seq.empty).filterNot(r => qRuleNames.contains(r.name)) // filter out dup rule names

    if(configRules.nonEmpty && queryRules.nonEmpty)
      logger.info(s"Engine-id: ${engineId}. Warning: duplicate rule names from the Query take precedence over config rules." +
        s"\n    Config rules: ${configRules.get}\n    Query rules: ${queryRules.get}\n")

    qRules ++ validConfigRules
  }

  /** Get recent events of the user on items to create the personalizing form of the recommendations query */
  private def getUserHistMatcher(query: URQuery): (Seq[Matcher], Seq[UREvent]) = {

    import DaoQuery.syntax._

    val queryEventNamesFilter = query.indicatorNames.getOrElse(modelEventNames) // indicatorParams in query take precedence
    // these are used in the MAP@k test to limit the indicators used for the query to measure the indicator's predictive
    // strength. DO NOT document, only for tests

    val userHistBias = query.userBias.getOrElse(userBias)
    val userEventsBoost = if (userHistBias > 0 && userHistBias != 1) Some(userHistBias) else None

    implicit val ordering = Ordering.desc
    val userHistory = eventsDao.findMany(
      orderBy = Some(OrderBy(ordering, fieldNames = "eventTime")),
      limit = maxQueryEvents
    )("entityId" === query.user.getOrElse("")).toSeq
      // .distinct // these will be distinct so this is redundant
      .filter { event =>
        queryEventNamesFilter.contains(event.event)
      }
      .map { event => // rename aliased events to the group name
        // logger.info(s"History: ${event}")
        val queryEventName = queryEventNames(event.event)
        event.copy(event = queryEventName)
      }
      .groupBy(_.event)
      .flatMap{ case (name, events) =>
        events.sortBy(_.eventTime) // implicit ordering
          .take(indicatorsMap(name).maxIndicatorsPerQuery.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType))
      }.toSeq

    val userEvents = modelEventNames.map { name =>
      (name, userHistory.filter(_.event == name).map(_.targetEntityId.get).distinct)
    }

    // logger.info(s"Number of user history indicators in ES query: ${userEvents.size}")
    // logger.info(s"Indicators in ES query: ${userEvents}")

    val userHistMatchers = userEvents.map { case(name, hist) => Matcher(name, hist, userEventsBoost) }
    (userHistMatchers, userHistory)
  }

  /** Get similar items for an item, these are already in the eventName correlators in ES */
  def getSimilarItemsMatchers(query: URQuery): Seq[Matcher] = {
    val activeItemBias = query.itemBias.getOrElse(itemBias)
    val similarItemsBoost = if (activeItemBias > 0 && activeItemBias != 1) Some(activeItemBias) else None

    query.item.fold(Seq.empty[Matcher]/*no item specified*/) { i =>
      logger.trace(s"Engine-id: ${engineId}. using item ${query.item.get}")
      val (_, itemProperties) = es.findDocById(i)

      logger.trace(s"Engine-id: ${engineId}. getBiasedSimilarItems for item $i, bias value ${itemBias}")
      modelEventNames.map { eventName => // get items that are similar by eventName
        val items: Seq[String] = itemProperties.getOrElse(eventName, Seq.empty[String])
        val rItems = items.take(maxQueryEvents)
        Matcher(eventName, rItems, similarItemsBoost)
      }
    }
  }

  private def getItemSetMatchers(query: URQuery): Seq[Matcher] = {
    query.itemSet.getOrElse(Seq.empty).map(item => Matcher(
      modelEventNames.head, // only look for items that have been converted on
      query.itemSet.getOrElse(Seq.empty),
      params.itemSetBias))
  }


  private def getBoostedRulesMatchers(aggregatedRules: Seq[Rule] = Seq.empty): Seq[Matcher] = {
    aggregatedRules.filter(rule => rule.bias > 0).map { rule =>
      Matcher(
        rule.name,
        rule.values,
        Some(rule.bias))
    }
  }

  private def getExcludeRulesMatchers(aggregatedRules: Seq[Rule] = Seq.empty): Seq[Matcher] = {
    aggregatedRules.filter(rule => rule.bias == 0).map { rule =>
      Matcher(
        rule.name,
        rule.values,
        None)
    }
  }

  private def getIncludeRulesMatchers(aggregatedRules: Seq[Rule] = Seq.empty): Seq[Matcher] = {
    aggregatedRules.filter(rule => rule.bias < 0).map { rule =>
      Matcher(
        rule.name,
        rule.values,
        Some(0))
    }
  }

  private def getBlacklistedItemsMatchers(query: URQuery, userEvents: Seq[UREvent]): Seq[Matcher] = {

    // blacklist things specified in the query plus the item itself for item-based queries
    val queryBlacklistWithItem =  if(query.item.isDefined && query.returnSelf.getOrElse(true)) { // item-based and not returnSelf
      query.blacklistItems.getOrElse(Seq.empty) :+ query.item.get
    } else { // ok to return the item in the query when using item-based queries
      query.blacklistItems.getOrElse(Seq.empty)
    }

    // now add the items in an item-set query to the blacklist if returnSelf is not true
    val queryBlacklist = if(query.itemSet.isDefined && query.returnSelf.getOrElse(true)) { // item-based and not returnSelf
      queryBlacklistWithItem ++ query.itemSet.get
    } else { // ok to return the item in the query when using item-based queries
      queryBlacklistWithItem
    }

    val blacklistByUserHistory = userEvents.filter(event => blacklistEvents.contains(event.event)).map(_.targetEntityId.getOrElse(""))
    Seq(
      Matcher(
        "id",
        (queryBlacklist ++ blacklistByUserHistory).distinct,
        None))
  }

  private def getDateFilters(query: URQuery): Seq[Filter] = {
    import com.actionml.core.search.syntax._
    val expirationSearchFilter = expireDateName.map(_ gt new Date)
    val availableSearchFilter = availableDateName.map(_ lt new Date)
    val itemDateRangeSearchFilterUpperLimit = for {
      name <- query.dateRange.map(_.name)
      value <- query.dateRange.map(_.before)
    } yield name lt value
    val itemDateRangeSearchFilterLowerLimit = for {
      name <- query.dateRange.map(_.name)
      value <- query.dateRange.map(_.after)
    } yield name gt value
    Seq(expirationSearchFilter, availableSearchFilter, itemDateRangeSearchFilterUpperLimit,
      itemDateRangeSearchFilterLowerLimit).flatten
  }

  /** Calculate all rules and items needed for ranking.
    *
    *  @param fieldsRDD all items with their rules
    *  @param sc the current Spark context
    *  @return
    */
  private def getRanksRDD(
    fieldsRDD: RDD[(ItemID, PropertyMap)],
    eventsRdd: RDD[UREvent],
    convertedItems: Seq[String])
    (implicit sc: SparkContext): RDD[(ItemID, PropertyMap)] = {

    val popModel = PopModel(fieldsRDD)
    val rankRDDs: Seq[(String, RDD[(ItemID, Double)])] = rankingsParams map { rankingParams =>
      val rankingType = rankingParams.`type`.getOrElse(DefaultURAlgoParams.BackfillType)
      val rankingFieldName = rankingParams.name.getOrElse(PopModel.nameByType(rankingType))
      val durationAsString = rankingParams.duration.getOrElse(DefaultURAlgoParams.BackfillDuration)
      val duration = Duration(durationAsString).toSeconds.toInt
      val backfillEvents = rankingParams.indicatorNames.getOrElse(modelEventNames.take(1))
      val offsetDate = rankingParams.offsetDate
      val rankRdd = popModel.calc(rankingType, eventsRdd, backfillEvents, duration, offsetDate)
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
      }.toMap ++ Map("id" -> ("keyword", true))
    logger.trace(s"Engine-id: ${engineId}. Index mappings for the Elasticsearch URModel: $mappings")
    mappings
  }

}

object URAlgorithm extends JsonSupport {

  def apply(engine: UREngine, initParams: String, dataset: URDataset): URAlgorithm = {
    val params = parseAndValidate[URAlgorithmParams](initParams, transform = _ \ "algorithm").andThen { params =>
      Valid(true, params)
    }.map(_._2).getOrElse(null.asInstanceOf[URAlgorithmParams])
    new URAlgorithm(engine, initParams, params, dataset.getIndicatorsDao, dataset.getItemsDao)
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
    val MaxEventsPerEventType = 500 // downsample to this amount of raw data randomly before model creation
    val NumResults = 20
    val MaxCorrelatorsPerEventType = 50 // number of correlated indicators to store in the model per type
    val MaxQueryEvents = 100 // default number of user history events to use in recs query for each indicator type

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
    val FudgeFactor = 10 // due to limitations with the DB query for user history we get n * FudgeFactor so we can
    // filter down to the best number per indicator type
  }

  case class RankingParams(
      name: Option[String] = Some(DefaultURAlgoParams.BackfillFieldName),
      `type`: Option[String] = Some(DefaultURAlgoParams.BackfillType), // See [[com.actionml.BackfillType]]
      indicatorNames: Option[Seq[String]] = None, // None means an error, events must be specified.
      offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
      // recent date for events going back by from the more recent offsetDate - duration
      endDate: Option[String] = None,
      duration: Option[String] = Some(DefaultURAlgoParams.BackfillDuration)) { // duration worth of events to use in calculation of backfill
    override def toString: String = {
      s"""
         |_id: $name,
         |type: ${`type`},
         |indicatorParams: $indicatorNames,
         |offsetDate: $offsetDate,
         |endDate: $endDate,
         |duration: $duration
      """.stripMargin
    }
  }

  case class DefaultIndicatorParams(
    aliases: Option[Seq[String]] = None,
    maxIndicatorsPerQuery: Int = DefaultURAlgoParams.MaxQueryEvents, // defaults to MaxQueryEvents == 100
    maxCorrelatorsPerItem: Int = DefaultURAlgoParams.MaxCorrelatorsPerEventType,
      // defaults to maxCorrelatorsPerEventType
    minLLR: Option[Double] = None) // defaults to none, used as a threshold, used with but takes precedence over maxCorrelatorsPerItem

  case class IndicatorParams(
    name: String, // must match one in indicatorParams
    aliases: Option[Seq[String]] = None,
    maxIndicatorsPerQuery: Option[Int], // used to limit query
    maxCorrelatorsPerItem: Option[Int], // used to limit model
    minLLR: Option[Double])

  case class URAlgorithmParams(
    indexName: Option[String], // can optionally be used to specify the elasticsearch index name
    typeName: Option[String], // can optionally be used to specify the elasticsearch type name
    recsModel: Option[String] = None, // "all", "collabFiltering", "backfill"
    // indicatorParams: Option[Seq[String]], // names used to ID all user indicatorRDDs
    blacklistIndicators: Option[Seq[String]] = None, // None means use the primary event, empty array means no filter
    // number of events in user-based recs query
    maxQueryEvents: Option[Int] = None,
    maxEventsPerEventType: Option[Int] = None,
    maxCorrelatorsPerEventType: Option[Int] = None,
    num: Option[Int] = None, // default max # of recs requested
    userBias: Option[Float] = None, // will cause the default search engine boost of 1.0
    itemBias: Option[Float] = None, // will cause the default search engine boost of 1.0
    itemSetBias: Option[Float] = None, // will cause the default search engine boost of 1.0
    returnSelf: Option[Boolean] = None, // query building logic defaults this to false
    rules: Option[Seq[Rule]] = None, //defaults to no rules
    // leave out for default or popular
    rankings: Option[Seq[RankingParams]] = None,
    // name of date property field for when the item is available
    availableDateName: Option[String] = None,
    // name of date property field for when an item is no longer available
    expireDateName: Option[String] = None,
    // used as the subject of a dateRange in queries, specifies the name of the item property
    dateName: Option[String] = None,
    indicators: Seq[IndicatorParams], // control params per matrix pair, every indicator must be listed at least
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

  /** Used to specify the date range for a query */
  case class DateRange(
      name: String, // name of item property for the date comparison
      before: Option[String], // empty strings means no filter
      after: Option[String]) // both empty should be ignored
    extends Serializable

  case class PreparedData(
      indicatorRDDs: Seq[(String, IndexedDataset)],
      fieldsRdd: RDD[(String, Map[String, Any])]) extends Serializable

  case class TrainingData(
    indicatorEvents: Seq[(String, RDD[(String, String)])],// indicator name, RDD[user-id, item-id]
    fieldsRDD: RDD[(String, Map[String, Any])], // RDD[ item-id, Map[String, Any] or property map
    minEventsPerUser: Option[Int] = Some(1)) extends Serializable

  case class TrainResponse(description: JobDescription, comment: String) extends Response
}

