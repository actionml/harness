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
import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.Date

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.engine._
import com.actionml.core.jobs.JobManager
import com.actionml.core.model.{Comment, Response}
import com.actionml.core.search.elasticsearch.ElasticSearchClient
import com.actionml.core.search.{Filter, Hit, Matcher, SearchQuery}
import com.actionml.core.spark.SparkContextSupport
import com.actionml.core.store.SparkMongoSupport.syntax._
import com.actionml.core.store.{DAO, DaoQuery, OrderBy, Ordering, SparkMongoSupport}
import com.actionml.core.validate.{JsonSupport, MissingParams, ValidateError}
import com.actionml.engines.ur.URAlgorithm.URAlgorithmParams
import com.actionml.engines.ur.UREngine.{ItemScore, Rule, UREvent, URItemProperties, URQuery, URQueryResult}
import com.sun.jndi.toolkit.dir
import org.apache.mahout.math.cf.{DownsamplableCrossOccurrenceDataset, SimilarityAnalysis}
import org.apache.mahout.math.indexeddataset.IndexedDataset
import org.apache.mahout.sparkbindings.indexeddataset.IndexedDatasetSpark
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


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
  with SparkMongoSupport
  with JsonSupport {

  import URAlgorithm._

  /*
  case class BoostableCorrelators(actionName: String, itemIDs: Seq[String], boost: Option[Float] = None) {
    def toFilterCorrelators: FilterCorrelators = {
      FilterCorrelators(actionName, itemIDs)
    }
  }
  case class FilterCorrelators(actionName: String, itemIDs: Seq[String])
  case class ExclusionFields(propertyName: String, values: Seq[String])
  */

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
  private var dateNames: Seq[String] = _
  private var es: ElasticSearchClient[Hit] = _
  private var indicators: Seq[IndicatorParams] = _
  private var seed: Option[Long] = None
  private var rules: Option[Seq[Rule]] = _
  private var availableDateName: Option[String] = _
  private var expireDateName: Option[String] = _
  private var itemDateName: Option[String] = _
  private var queryEventNames: Map[String, String] = _


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
        previous + indicator.maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxQueryEvents)
    } * 10


    indicators = params.indicators
    seed = params.seed
    rules = params.rules
    availableDateName = params.availableDateName
    expireDateName = params.expireDateName
    itemDateName = params.dateName
    indicatorParams = params.indicators


    // continue validating if all is ok so far
    err.andThen { isOK =>
      // create a Map of alias -> indicator name or indicator name -> indicator name if no aliases
      queryEventNames = indicatorParams.flatMap { case i =>
        val aliases = i.aliases.getOrElse(Seq(i.name))
        if(i.name == aliases.head && aliases.size == 1) {
          Map(i.name -> i.name)
        } else {
          aliases.map(_ -> i.name)
        }.toMap
      }.toMap

      logger.info(s"Events to alias mapping: ${queryEventNames}")
      limit = params.num.getOrElse(DefaultURAlgoParams.NumResults)

      modelEventNames = params.indicators.map(_.name)

      blacklistEvents = params.blacklistEvents.getOrElse(Seq(modelEventNames.head)) // empty Seq[String] means no blacklist
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
    val jobDescription = JobManager.addJob(engineId, "Spark job")
    val f = SparkContextSupport.getSparkContext(initParams, engineId, jobDescription, kryoClasses = Array(classOf[UREvent]))
    f.map { implicit sc =>
      logger.info(s"Spark context spark.submit.deployMode: ${sc.deployMode}")
      try {
        val eventsRdd = eventsDao.readRdd[UREvent](MongoStorageHelper.codecs)
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
      } catch {
        case e: Throwable =>
          logger.error(s"Spark computation failed for job $jobDescription", e)
          sc.cancelJobGroup(jobDescription.jobId)
          throw e
      } finally {
        JobManager.removeJob(jobDescription.jobId)
        SparkContextSupport.stopAndClean(sc)
      }
    }

    // todo: EsClient.close() can't be done because the Spark driver might be using it unless its done in the Furute
    logger.debug(s"Starting train $this with spark")
    Valid(Comment("Started train Job on Spark"))
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

    val convertedItems = data.indicatorRDDs.filter { case (en, ids) => en == modelEventNames.head}
      .head._2.columnIDs.toMap.keySet.toSeq

    logger.info("Actions read now creating correlators")
    val cooccurrenceIDSs = {
      val iDs = data.indicatorRDDs.map(_._2).toSeq
      val datasets = iDs.zipWithIndex.map {
        case (iD, i) =>
          DownsamplableCrossOccurrenceDataset(
            iD,
            indicators(i).maxItemsPerUser.getOrElse(DefaultURAlgoParams.MaxEventsPerEventType),
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

    //val collectedProps = propertiesRDD.collect()

    logger.info("Correlators created now putting into URModel")
    new URModel(
      coocurrenceMatrices = cooccurrenceCorrelators,
      propertiesRDDs = Seq(propertiesRDD),
      typeMappings = getMappings)(sc, es)
  }


  def query(query: URQuery): URQueryResult = {
    // todo: need to hav an API to see if the alias and index exist. If not then send a friendly error message
    // like "you forgot to train"
    // todo: order by date
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
    val result = es.search(modelQuery).map(hit => ItemScore(hit.id, hit.score)) // todo: optionally return rankings?
    URQueryResult(result)
  }

  private def buildModelQuery(query: URQuery): SearchQuery = {
    val queryEventNames = query.eventNames.getOrElse(modelEventNames) // indicatorParams in query take precedence
    val aggregatedRules = aggregateRules(rules, query.rules)

    logger.info(s"Got query: \n${query}")

    val startPos = query.from.getOrElse(0)
    val numResults = query.num.getOrElse(limit)

    // create a list of all query correlators that can have a bias (boost or filter) attached
    val (userHistoryMatchers, userEvents) = getUserHistMatcher(query)
    val shouldMatchers = Map("terms" -> (userHistoryMatchers ++
      getSimilarItemsMatchers(query) ++
      getItemSetMatchers(query) ++
      getBoostedRulesMatchers(aggregatedRules)))

    val mustMatchers = Map("terms" -> (getIncludeRulesMatchers(aggregatedRules)))

    val mustNotMatchers = Map("terms" -> (getExcludeRulesMatchers(aggregatedRules) ++
      getBlacklistedItemsMatchers(query, userEvents)))

    val sq =SearchQuery(
      sortBy = rankingsParams.head.name.getOrElse("popRank"), // todo: this should be a list of ranking rules
      should = shouldMatchers,
      must = mustMatchers,
      mustNot = mustNotMatchers,
      filters = getDateFilters(query),
      size = numResults,
      from = startPos
    )

    import org.json4s.jackson.Serialization.write

    //logger.info(s"Formed SearchQuery:\n${sq}\nJSON:${prettify(write(sq))}")
    sq
  }

  /** Aggregates unique Rules by name, discarding config rules that are named the same as a query rule */
  private def aggregateRules(configRules: Option[Seq[Rule]] = None, queryRules: Option[Seq[Rule]] = None): Seq[Rule] = {
    val qRules = queryRules.getOrElse(Seq.empty)
    val qRuleNames = qRules.map(_.name)
    val validConfigRules = configRules.getOrElse(Seq.empty).filterNot(r => qRuleNames.contains(r.name)) // filter out dup rule names

    if(configRules.nonEmpty && queryRules.nonEmpty)
      logger.info(s"Warning: duplicate rule names from the Query take precedence." +
        s"\n    Config rules: ${configRules.get}\n    Query rules: ${queryRules.get}\n")

    qRules ++ validConfigRules
  }

  /** Get recent events of the user on items to create the personalizing form of the recommendations query */
  private def getUserHistMatcher(query: URQuery): (Seq[Matcher], Seq[UREvent]) = {

    import DaoQuery.syntax._

    val userHistBias = query.userBias.getOrElse(userBias)
    val userEventsBoost = if (userHistBias > 0 && userHistBias != 1) Some(userHistBias) else None

    val userHistory = eventsDao.findMany(
      DaoQuery(
        orderBy = Some(OrderBy(ordering = Ordering.desc, fieldNames = "eventTime")),
        limit= maxQueryEvents * 100, // * 100 is a WAG since each event type should have maxQueryEvents todo: should set per indicator
        // todo: should get most recent events per eventType since some may be sent only once to indicate user properties
        // and these may have very old timestamps, ALSO DO NOT TTL THESE, create a user property DAO to avoid event TTLs ????
        filter = Seq("entityId" === query.user.getOrElse(""))))
      .toSeq
      .distinct
      .map { event =>
        val queryEventName = queryEventNames(event.event)
        event.copy(event = queryEventName)
      }

    val userEvents = modelEventNames.map { name =>
      (name, userHistory.filter(_.event == name).map(_.targetEntityId.get).toSeq.distinct)
    }

    val userHistMatchers = userEvents.map { case(name, hist) => Matcher(name, hist, userEventsBoost) }
    (userHistMatchers, userHistory)
  }

  /** Get similar items for an item, these are already in the eventName correlators in ES */
  def getSimilarItemsMatchers(query: URQuery): Seq[Matcher] = {
    val activeItemBias = query.itemBias.getOrElse(itemBias)
    val similarItemsBoost = if (activeItemBias > 0 && activeItemBias != 1) Some(activeItemBias) else None

    if (query.item.nonEmpty) {
      logger.info(s"using item ${query.item.get}")
      val (item, itemProperties) = es.findDocById(query.item.get, esType)

      logger.info(s"getBiasedSimilarItems for item ${query.item.get}, bias value ${itemBias}")
      modelEventNames.map { eventName => // get items that are similar by eventName
        val items: Seq[String] = itemProperties.getOrElse(eventName, Seq.empty[String])
        val rItems = items.take(maxQueryEvents)
        Matcher(eventName, rItems, similarItemsBoost)
      }
    } else {
      Seq.empty
    } // no item specified
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
      val backfillEvents = rankingParams.eventNames.getOrElse(modelEventNames.take(1))
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
      }
    logger.info(s"Index mappings for the Elasticsearch URModel: $mappings")
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
      eventNames: Option[Seq[String]] = None, // None means use the algo indicatorParams findMany, otherwise a findMany of events
      offsetDate: Option[String] = None, // used only for tests, specifies the offset date to start the duration so the most
      // recent date for events going back by from the more recent offsetDate - duration
      endDate: Option[String] = None,
      duration: Option[String] = None) { // duration worth of events to use in calculation of backfill
    override def toString: String = {
      s"""
         |_id: $name,
         |type: ${`type`},
         |indicatorParams: $eventNames,
         |offsetDate: $offsetDate,
         |endDate: $endDate,
         |duration: $duration
      """.stripMargin
    }
  }

  case class DefaultIndicatorParams(
    aliases: Option[Seq[String]] = None,
    maxItemsPerUser: Int = DefaultURAlgoParams.MaxQueryEvents, // defaults to maxEventsPerEventType
    maxCorrelatorsPerItem: Int = DefaultURAlgoParams.MaxCorrelatorsPerEventType,
      // defaults to maxCorrelatorsPerEventType
    minLLR: Option[Double] = None) // defaults to none, takes precendence over maxCorrelatorsPerItem

  case class IndicatorParams(
    name: String, // must match one in indicatorParams
    aliases: Option[Seq[String]] = None,
    maxItemsPerUser: Option[Int], // defaults to maxEventsPerEventType
    maxCorrelatorsPerItem: Option[Int], // defaults to maxCorrelatorsPerEventType
    minLLR: Option[Double]) // defaults to none, takes precendence over maxCorrelatorsPerItem

  case class URAlgorithmParams(
    indexName: Option[String], // can optionally be used to specify the elasticsearch index name
    typeName: Option[String], // can optionally be used to specify the elasticsearch type name
    recsModel: Option[String] = None, // "all", "collabFiltering", "backfill"
    // indicatorParams: Option[Seq[String]], // names used to ID all user indicatorRDDs
    blacklistEvents: Option[Seq[String]] = None, // None means use the primary event, empty array means no filter
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

}

