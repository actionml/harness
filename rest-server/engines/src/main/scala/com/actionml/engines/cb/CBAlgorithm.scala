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

package com.actionml.engines.cb

import java.io.{File, IOException}
import java.nio.file.attribute.{PosixFileAttributes, PosixFilePermission}
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

import akka.actor._
import akka.event.Logging
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.model.{Comment, GenericEngineParams, Response, User}
import com.actionml.core.store._
import com.actionml.core.store.backends.MongoAsyncDao
import com.actionml.core.engine._
import com.actionml.core.validate.{JsonSupport, ValidRequestExecutionError, ValidateError}
import com.actionml.engines.cb.SingleGroupTrainer.constructVWString
import com.typesafe.scalalogging.{LazyLogging, Logger}

import scala.concurrent.Await
import scala.io.Source
import scala.reflect.io.Path
import scala.util.{Failure, Success}
import scala.util.control.Breaks
//import java.io.File
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, JValue, MappingException}
import org.slf4j.event.SubstituteLoggingEvent

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.ConfigFactory
import vowpalWabbit.learner._

case class CBAlgorithmInput(
    user: User,
    event: CBUsageEvent,
    testGroup: GroupParams,
    resourceId: String )
  extends AlgorithmInput

case class Train(datum: CBAlgorithmInput)

/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the ScaffoldAlgorithm and will be added and killed when needed.
  */
class CBAlgorithm(json: String, resourceId: String, dataset: CBDataset)
  extends Algorithm[CBQuery, CBQueryResult] with KappaAlgorithm[CBAlgorithmInput] with JsonSupport {

  private val actors = ActorSystem(resourceId)
  actors.whenTerminated.onComplete {
    case r => logger.info(s"ActorSystem: $actors for engine: $resourceId with dataset: $dataset terminated with result: $r")
  }

  var trainers = Map.empty[String, ActorRef]
  var params: CBAlgoParams = _

  // from the Dataset determine which groups are defined and start training on them

  var vw: VWMulticlassLearner = _
  var events = 0
  var cache_path: String = _


  override def init(engine: Engine): Validated[ValidateError, Response] = {
    super.init(engine).andThen { _ =>
      parseAndValidate[CBAllParams](json).andThen { p =>
        params = p.algorithm.copy(
          namespace = engineId)
        val groupEvents: Map[String, DAO[UsageEvent]] = dataset.usageEventGroups
        logger.trace(s"Init algorithm for ${groupEvents.size} groups. ${groupEvents.mkString(", ")}")
        val exists = trainers.keys.toList
        cache_path = s"${modelPath}_cache"
        vw = createVW(params) // sets up the parameters for the model and names the file for store of the\\

        groupEvents.foreach(groupName => add(groupName._1))
        // params .close() should write to the file

        Valid(Comment("Init processed"))
      }
    }
  }

  override def input(datum: CBAlgorithmInput): Validated[ValidateError, String]= {
    events += 1
    // if (events % 20 == 0) checkpointVW(params)
    val groupName = datum.event.toUsageEvent.testGroupId
    try {
      logger.trace(s"Train trainer $groupName, with datum: $datum")
      trainers(groupName) ! Train(datum)
      checkpointVW(params) // todo: may miss some since train is in an Actor, should try the pseudo-param to saveOneById model
      Valid(jsonComment("Input processed"))
    } catch {
      case e: NoSuchElementException =>
        logger.error(s"Training triggered on non-existent group: $groupName Initialize the group before sending input.", e)
        Invalid(ValidRequestExecutionError(jsonComment(s"Input to non-existent group: $groupName Initialize the group before sending input.")))
    }
  }

  override def query(query: CBQuery): Future[CBQueryResult] = {
    // todo: isDefinedAt is not enough to know there have been events
    if (dataset.usageEventGroups isDefinedAt query.groupId) {
      logger.info(s"Making query for group: ${query.groupId}")
      getVariant(query)
    } else {
      logger.info(s"Making default query since dataset.usageEventGroups isDefinedAt query.groupId is false for group: ${query.groupId}")
      getDefaultVariant(query)
    }
    ???
  }

  def createVW(params: CBAlgoParams): VWMulticlassLearner = {
    val regressorType = s" --csoaa 10 "
    val reg = s" --l2 ${params.regParam} "
    val iters = s" -k --passes ${params.maxIter} " // must have -c OR specify a cache_file
    val lrate = s" -l ${params.stepSize} "
    val cacheFile = s" --cache_file ${cache_path} " // not used, let VW decide how to do caching
    val bitPrecision = s" -b ${params.bitPrecision.toString} "
    val checkpointing = " --save_resume "
    val newModel = s" -f ${modelPath} "
    val trainedModel = s" -i ${modelPath} "

    // if the modelName already exists, use it to initialize VW otherwise, create a new model
    // the difference is -i for initial model, or -f for new model (I think)
    val initVWConfig = trainedModel + checkpointing
    val createVWConfig = regressorType + bitPrecision + reg + lrate + iters + newModel + checkpointing + cacheFile
    val newVW: VWMulticlassLearner = if (fileExists(modelPath)) {
      logger.info(s"VW: config: \n$initVWConfig\n")
      VWLearners.create(initVWConfig)
    } else {
      logger.info(s"VW: config: \n$createVWConfig\n")
      VWLearners.create(createVWConfig)
    }
    newVW
  }

  def checkpointVW(params: CBAlgoParams): Unit = {
    logger.info(s"Checkpointing model")
    vw.saveModel(new File(modelPath))
  }

  def fileExists(modelpath: String): Boolean = {
    logger.trace(s"Looking at $modelpath to see if there is an existing file")
    try {
      val modelFile = new File(modelpath)
      if (modelFile.exists() && !modelFile.isDirectory) true else false
    } catch {
      case _: Exception =>
        val errMsg = s"Problem finding model at: $modelpath"
        logger.error(errMsg)
        false
    }
  }

  def remove(groupName: String): Unit = {
    try {
      logger.info("Stop trainer {}", groupName)
      actors stop trainers(groupName)
      logger.info("Remove trainer {}", groupName)
      trainers -= groupName
    } catch {
      case e: NoSuchElementException =>
        logger.error(s"Deleting non-existent group: $groupName The group must be initialized first. Ingoring event.")
    }
  }

  def add(groupName: String): Unit = {
    logger.info("Create trainer {}", groupName)
    if (!trainers.contains(groupName) && dataset.groupsDao.findOneById(groupName).nonEmpty) {
      val actor = actors.actorOf(
        SingleGroupTrainer.props(
          dataset.usageEventGroups(groupName),
          dataset.usersDAO,
          params,
          dataset.groupsDao.findOneById(groupName).get,
          engineId,
          modelPath,
          this.asInstanceOf[CBAlgorithm]),
        groupName)
      trainers += groupName → actor
    }
  }

 def getVariant(query: CBQuery): CBQueryResult = {
    //val vw = new VW(" -i " + modelContainer)

    val group = dataset.groupsDao.findOneById(query.groupId).get

    val numClasses = group.pageVariants.size

    //val classString = (1 to numClasses).mkString(" ") // todo: use keys in pageVariants 0..n
    val classString = group.pageVariants.keySet.mkString(" ")

    val user = dataset.usersDAO.findOneById(query.user).getOrElse(User("", Map.empty))

    val queryText = SingleGroupTrainer.constructVWString(classString, user._id, query.groupId, user, engineId)


    logger.info(s"Query string to VW: \n$queryText")
    val pred = vw.predict(queryText)
    logger.info(s"VW: raw results, not yet run through probability distribution: ${pred}\n\n")
    //vw.close() // not need to saveOneById the model for a query

    //see http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.109.4518&rep=rep1&type=pdf
    //we use testPeriods as epsilon0

    val startTime = group.testPeriodStart
    val endTime = group.testPeriodEnd.getOrElse(OffsetDateTime.now) // Todo: no end means you are always on the last day, no
    // randomness

    val maxEpsilon = 1.0 - (1.0 / numClasses)
    val currentTestDuration = Duration.between(startTime, OffsetDateTime.now).toMinutes.toDouble
    val totalTestDuration = Duration.between(startTime, endTime).toMinutes.toDouble

    // Alex: scale epsilonT to the range 0.0-maxEpsilon
    // Todo: this is wacky, it introduces too much randomness and none if there is no test period end--ugh!
    val epsilonT = scala.math.max(0, scala.math.min(maxEpsilon, maxEpsilon * (1.0 - currentTestDuration / totalTestDuration)))

    // the key->value needs to be in the DB when a test-group is defined
    val groupMap = group.pageVariants.map(a => a._1.toInt -> a._2)

    val probabilityMap = groupMap.keys.map { keyInt =>
      keyInt -> (
        if (keyInt == pred)
          1.0 - epsilonT
        else
          epsilonT / (numClasses - 1.0)
        )
    }.toMap

    val sampledPred = sample(probabilityMap)

    // todo: disables sampling
    val pageVariant = groupMap(sampledPred)
    //val pageVariant = groupMap(pred.head.getAction)
    CBQueryResult(pageVariant, groupId = query.groupId)
  }


  // this is likely to not be called since we don't keep track of whether a group has ever received training data
  def getDefaultVariant(query: CBQuery): CBQueryResult = {
    logger.info("Test group has no training data yet. Fall back to uniform distribution")
    val group = dataset.groupsDao.findOneById(query.groupId).get

    val variants = group.pageVariants
    val numClasses = variants.size
    val map = variants.map { x => x._2 -> 1.0 / numClasses }.toMap
    CBQueryResult(sample[String](map), groupId = query.groupId)
  }

  def sample[A](dist: Map[A, Double]): A = {
    val p = scala.util.Random.nextDouble

    val rangedProbs = dist.values.scanLeft(0.0)(_ + _).drop(1)

    val rangedMap = (dist.keys zip rangedProbs).toMap

    val item = dist.filter(x => rangedMap(x._1) >= p).keys.head

    item
  }

  override def destroy(): Unit = {

    try{ Await.result(
      actors.terminate().andThen { case _ =>
        logger.debug("Closing VW learner")
        if (vw != null.asInstanceOf[VWMulticlassLearner]) vw.close()
      }.map { _ =>
        logger.debug(s"Attempting to delete old model file: $modelPath")
        if (Files.exists(Paths.get(modelPath)) && !Files.isDirectory(Paths.get(modelPath))) {
          while (!Files.deleteIfExists(Paths.get(modelPath))) {
            logger.info(s"Could not delete the model: $modelPath, trying again.")
          }
          logger.info(s"Success deleting model: $modelPath")
        }
        logger.debug(s"Attempting to delete old cache file: $cache_path")
        if (Files.exists(Paths.get(cache_path)) && !Files.isDirectory(Paths.get(cache_path))) {
          while (!Files.deleteIfExists(Paths.get(cache_path))) {
            logger.info(s"Could not delete the cache: $cache_path, trying again.")
          }
          logger.info(s"Success deleting cache: $cache_path")
        }
      }, 5 seconds)
    } catch {
      case e: TimeoutException =>
        logger.error(s"Error unable to delete the VW model file for $engineId at $modelPath in the 5 second timeout.")
    }
  }

}

case class CBAllParams(
  algorithm: CBAlgoParams)


case class CBAlgoParams(
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams


class SingleGroupTrainer(
    events: DAO[UsageEvent],
    users: DAO[User],
    params: CBAlgoParams,
    group: GroupParams,
    resourceId: String,
    modelPath: String,
    cbAlgo: CBAlgorithm)
  extends ActorWithLogging {




  import SingleGroupTrainer._

  override def receive: Receive = {
    case t: Train ⇒
      log.debug(s"$name Receive 'Train', run group training")
      train(t.datum)
  }

  private def train(input: CBAlgorithmInput): Unit = {
    log.debug(s"$name Start work")
    val vwString: String = eventToVWStrings(
      input.event,
      input.testGroup.pageVariants.map( a => a._1.toInt -> a._2),
      input.user,
      input.resourceId)


    if (vwString != null.asInstanceOf[String] && vwString.nonEmpty && cbAlgo.vw != null.asInstanceOf[VWMulticlassLearner]) {
      log.info(s"Sending the VW formatted string: \n$vwString")
      val result = cbAlgo.vw.learn(vwString)
      log.info(s"Saving model for path: $modelPath")
      cbAlgo.vw.learn(s" save_${modelPath}")// saveOneById after every event
/*      examples += 1
      if (examples % 20 == 0) { // saveOneById every 20 events
        log.info(s"Got result to vw.learn(usageEvent) of: ${result.toString}")
        log.info(s"Sending the pseudo example to saveOneById the model: save_${params.modelName}")
        result = cbAlgo.vw.learn(s" save_${result.toString} ") // pseudo example that tells VW to checkpoint the model
        log.info(s"Got result to saveOneById of: ${result.toString}")
      }
*/
    } else {
      log.error(s"Error converting $input into VW string or VW is null, meaning it has crashed?")
    }
    //for ( item <- inputs ) log.info(s"$item\n")

  }

  def eventToVWStrings(
    event: UsageEvent,
    variants: Map[Int, String],
    user: User,
    resourceId: String): String = {

    //val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())

    //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it, which is never the case since we train per group now, not all groups.
    val classString: String = variants.map { case( variantIntKey, variantLable) =>
      variantIntKey.toString + ":" +
        //(if (thisClass._2 == example.variant && example.converted) "0.0"
        (if (variantLable == event.itemId && event.converted) "0.0"
        else if (variantLable == event.itemId ) "2.0"
        else "1.0")
    }.mkString(" ")

    constructVWString(classString, event.userId, event.testGroupId, user, resourceId)

  }

  def eventToVWStrings(
    event: CBUsageEvent,
    variants: Map[Int, String],
    user: User,
    resourceId: String): String = {

    //val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())

    //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it, which is never the case since we train per group now, not all groups.
    val classString: String = variants.map { case( variantIntKey, variantLable) =>
      variantIntKey.toString + ":" +
        //(if (thisClass._2 == example.variant && example.converted) "0.0"
        (if (variantLable == event.targetEntityId && event.properties.converted) "0.0"
        else if (variantLable == event.targetEntityId ) "2.0"
        else "1.0")
    }.mkString(" ")

    constructVWString(classString, event.entityId, event.properties.testGroupId, user, resourceId)

  }

}

object SingleGroupTrainer {


  def props(
    events: DAO[UsageEvent],
    users: DAO[User],
    params: CBAlgoParams,
    group: GroupParams,
    resourceId: String,
    modelPath: String,
    cbAlgo: CBAlgorithm): Props = Props(new SingleGroupTrainer(events, users, params, group, resourceId, modelPath, cbAlgo))

  /* def constructVWString(
       classString: String,
       user: String,
       testGroupId: String,
       userProps: Map[String,PropertyMap]): String = {

       @transient implicit lazy val formats = org.json4s.DefaultFormats

       classString + " |" +  ap.namespace + " " +
         rawTextToVWFormattedString(
           "user_" + user + " " +
           "testGroupId_" + testGroupId + " " +
           (userProps.getOrElse(user, PropertyMap(Map[String,JValue](), new DateTime(), new DateTime())) -- List("converted", "testGroupId")).fields.map { entry =>
              entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId }.mkString(" "))
  }
  */

  def constructVWString(
    classString: String,
    userId: String,
    testGroupId: String,
    user: User,
    resourceId: String): String = {

    @transient implicit lazy val formats = org.json4s.DefaultFormats

    // class-id|namespace user_ user testGroupId_ testGroupId
    // (${user properties findMany} -- List("converted", "testGroupId")).fields.map { entry =>
    //   entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId
    // }.mkString(" ")

    //val vwString = classString + " |" +  engineId + " " + // may need to make a namespace per group by engineId+testGroupId
    val vwString = classString + " | " + // may need to make a namespace per group by engineId+testGroupId
      rawTextToVWFormattedString(
        "user_" + userId + " " + "testGroupId_" + testGroupId + " " +
          user.propsToMapOfSeq.map { case(propId, propSeq) =>
            propSeq.map { propVal  =>
              propId + "_" + propVal.replaceAll("\\s+", "_") + "_" + testGroupId
            }.mkString(" ")
          }.mkString(" "))
    //log.info(s"VW string for training: $vwString")
    vwString
  }

  def rawTextToVWFormattedString(str: String) : String = {
    //VW input cannot contain these characters
    val ret = str.replaceAll("[|:]", "")
    ret
  }

}

trait ActorWithLogging extends Actor with ActorLogging{

  protected val name: String = self.path.name

  override def preStart(): Unit = {
    super.preStart()
    log.debug(s"${Console.GREEN}Start actor $name${Console.RESET}")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.debug(s"${Console.RED}Stop actor $name${Console.RESET}")
  }

}
