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

import java.io.File
import java.util.concurrent.TimeoutException

import akka.actor._
import akka.event.Logging
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage._
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError}
import com.actionml.templates.cb.SingleGroupTrainer.constructVWString
import com.mongodb.casbah.Imports._
import salat.global._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.io.Source
import scala.reflect.io.Path
import scala.tools.nsc.classpath.FileUtils
import scala.util.control.Breaks
//import java.io.File
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.{MongoDBObject, TypeImports}
import org.joda.time.{DateTime, Duration}
import org.json4s.ext.JodaTimeSerializers
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

/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the ScaffoldAlgorithm and will be added and killed when needed.
  */
case class CBAlgorithmInput(
    user: User,
    event: CBUsageEvent,
    testGroup: GroupParams,
    resourceId: String )
  extends AlgorithmInput

case class Train(datum: CBAlgorithmInput)

class CBAlgorithm(dataset: CBDataset)
  extends Algorithm[CBQuery, CBQueryResult] with KappaAlgorithm[CBAlgorithmInput] with JsonParser with Mongo {

  val serverHome = sys.env("HARNESS_HOME")

  private val actors = ActorSystem("CBAlgorithm") // todo: should this be derived from the classname?
  var trainers = Map.empty[String, ActorRef]
  var params: CBAlgoParams = _
  var resourceId: String = _
  var modelPath: String = _

  // from the Dataset determine which groups are defined and start training on them

  var vw: VWMulticlassLearner = _
  var events = 0

  override def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    //val response = parseAndValidate[ScaffoldAlgoParams](json)
    resourceId = rsrcId
    parseAndValidate[CBAllParams](json).andThen { p =>
      modelPath = p.algorithm.modelContainer.getOrElse(serverHome) + resourceId
      params = p.algorithm.copy(
        modelName = modelPath,
        namespace = resourceId)
      val groupEvents: Map[String, UsageEventDAO] = dataset.usageEventGroups
      logger.trace(s"Init algorithm for ${groupEvents.size} groups. ${groupEvents.mkString(", ")}")
      val exists = trainers.keys.toList
      /*val diff = groupEvents.filterNot { case (key, _) =>
        exists.contains(key) && dataset.GroupsDAO.findOne(DBObject("_id" -> key)).nonEmpty
      }

      diff.foreach { case (trainer, collection) =>
        val group = dataset.GroupsDAO.findOne(DBObject("_id" -> trainer)).get
        val actor = actors.actorOf(SingleGroupTrainer.props(collection, dataset.usersDAO, params, group, resourceId,
          modelPath), trainer)
        trainers += trainer → actor
      }
      */

      // do this before any actors are created since they need the VW instance
      vw = createVW(params) // sets up the parameters for the model and names the file for storage of the\\

      groupEvents.foreach( groupName => add(groupName._1))
      // params .close() should write to the file

      Valid(true)
    }
  }

  override def input(datum: CBAlgorithmInput): Validated[ValidateError, Boolean] = {
    events += 1
    if (events % 20 == 0) checkpointVW(params)
    val groupName = datum.event.toUsageEvent.testGroupId
    try {
      logger.trace(s"Train trainer $groupName, with datum: $datum")
      trainers(groupName) ! Train(datum)
      Valid(true)
    } catch {
      case e: NoSuchElementException =>
        logger.error(s"Training triggered on non-existent group: $groupName Initialize the group before sending input.")
        Invalid(ValidRequestExecutionError(s"Input to non-existent group: $groupName Initialize the group before sending input."))
    }
  }

  override def predict(query: CBQuery): CBQueryResult = {
    // todo: isDefinedAt is not enough to know there have been events
    if(dataset.usageEventGroups isDefinedAt query.groupId) getVariant(query) else getDefaultVariant(query)
  }

  override def destroy(): Unit = {
    // remove old model since it is recreated with each new CBEngine
    // the VW model file may take some time to be deletable after closing vw?????
    if (vw != null.asInstanceOf[VWMulticlassLearner]) vw.close() //Todo: may have to put in future and wait with timeout
    // used by 'time' method
    implicit val baseTime = System.currentTimeMillis

    // put a time limit for VW to close and release the model file
    val deleteModel = Future {
      if (Files.exists(Paths.get(modelPath)) && !Files.isDirectory(Paths.get(modelPath)))
        while (!Files.deleteIfExists(Paths.get(modelPath))) {}
    }
    // Todo: should allow configurable?
    try{ Await.result(deleteModel, 2 seconds) } catch {
      case e: TimeoutException =>
        logger.error(s"Error unable to delete the VW model file for $resourceId at $modelPath in the 2 second timeout.")
    }

  }

  def createVW(params: CBAlgoParams): VWMulticlassLearner = {
    val regressorType = s" --csoaa 10 "
    val reg = s" --l2 ${params.regParam} "
    val iters = s" -c -k --passes ${params.maxIter} "
    val lrate = s" -l ${params.stepSize} "
    val cacheFile = s" --cache_file ${params.modelName}_cache " // Todo: not used because can't have more than one?????
    val bitPrecision = s" -b ${params.bitPrecision.toString} "
    val checkpointing = " --save_resume "
    val newModel = s" -f ${params.modelName} "
    val trainedModel = s" -i ${params.modelName} "

    // if the modelName already exists, use ot to initialize VW otherwise, create a new model
    // the difference is -i for initial model, or -f for new model (I think)
    val initVWConfig = trainedModel + checkpointing
    val createVWConfig = regressorType + bitPrecision + reg + lrate + iters + newModel + checkpointing
    val newVW: VWMulticlassLearner = if (fileExists(params.modelName)) {
      logger.info(s"VW: config: \n$initVWConfig\n")
      VWLearners.create(initVWConfig)
    } else {
      logger.info(s"VW: config: \n$createVWConfig\n")
      VWLearners.create(createVWConfig)
    }
    newVW
  }

  def checkpointVW(params: CBAlgoParams): Unit = {

    val regressorType = s" --csoaa 10 "
    val reg = s" --l2 ${params.regParam} "
    val iters = s" -c -k --passes ${params.maxIter} "
    val lrate = s" -l ${params.stepSize} "
    val cacheFile = s" --cache_file ${params.modelName}_cache " // Todo: not used because can't have more than one?????
    val bitPrecision = s" -b ${params.bitPrecision.toString} "
    val checkpointing = " --save_resume "
    val newModel = s" -f ${params.modelName} "
    val trainedModel = s" -i ${params.modelName} "

    // holly crap this is the only way to get a model saved??????????
    val initVWConfig = trainedModel + checkpointing
    // vw.close() // should checkpoint
    // vw = VWLearners.create(initVWConfig).asInstanceOf[VWMulticlassLearner] // should open the checkpointed file
    logger.trace(s"Checkpointing model")
    vw.saveModel(new File(params.modelName))
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
    } catch{
      case e: NoSuchElementException =>
        logger.error(s"Deleting non-existent group: $groupName The group must be initialized first. Ingoring event.")
    }
  }

  def add(groupName: String): Unit = {
    logger.info("Create trainer {}", groupName)
    if (!trainers.contains(groupName) &&  dataset.GroupsDAO.findOneById(groupName).nonEmpty) {
      val actor = actors.actorOf(
        SingleGroupTrainer.props(
          dataset.usageEventGroups(groupName),
          dataset.usersDAO,
          params,
          dataset.GroupsDAO.findOneById(groupName).get,
          resourceId,
          modelPath,
          this.asInstanceOf[CBAlgorithm]),
        groupName)
      trainers += groupName → actor
    }
  }

  override def stop(): Unit = {
    actors.terminate().wait()
  }

  def getVariant(query: CBQuery): CBQueryResult = {
    //val vw = new VW(" -i " + modelPath)

    val group = dataset.GroupsDAO.findOneById(query.groupId).get

    val numClasses = group.pageVariants.size

    //val classString = (1 to numClasses).mkString(" ") // todo: use keys in pageVariants 0..n
    val classString = group.pageVariants.keySet.mkString(" ")

    val user = dataset.usersDAO.findOneById(query.user).getOrElse(User("",Map.empty))

    val queryText = SingleGroupTrainer.constructVWString(classString, user._id, query.groupId, user, resourceId)


    logger.info(s"Query string to VW: \n$queryText")
    val pred = vw.predict(queryText)
    logger.info(s"VW: raw results, not yet run through probability distribution: ${pred}\n\n")
    //vw.close() // not need to save the model for a query

    //see http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.109.4518&rep=rep1&type=pdf
    //we use testPeriods as epsilon0

    val startTime = group.testPeriodStart
    val endTime = group.testPeriodEnd.getOrElse(new DateTime()) // Todo: no end means you are always on the last day, no
    // randomness

    val maxEpsilon = 1.0 - (1.0/numClasses)
    val currentTestDuration = new Duration(startTime, new DateTime()).getStandardMinutes().toDouble
    val totalTestDuration = new Duration(startTime, endTime).getStandardMinutes().toDouble

    // Alex: scale epsilonT to the range 0.0-maxEpsilon
    // Todo: this is wacky, it introduces too much randomness and none if there is no test period end--ugh!
    val epsilonT = scala.math.max(0, scala.math.min(maxEpsilon, maxEpsilon * (1.0 - currentTestDuration/ totalTestDuration) ))

    // the key->value needs to be in the DB when a test-group is defined
    val groupMap = group.pageVariants.map( a => a._1.toInt -> a._2)

    val probabilityMap = groupMap.keys.map { keyInt =>
      keyInt -> (
        if(keyInt == pred)
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
    val group = dataset.GroupsDAO.findOneById(query.groupId).get

    val variants = group.pageVariants
    val numClasses = variants.size
    val map = variants.map{ x => x._2 -> 1.0/numClasses}.toMap
    CBQueryResult(sample[String](map), groupId = query.groupId)
  }

  def sample[A](dist: Map[A, Double]): A = {
    val p = scala.util.Random.nextDouble

    val rangedProbs = dist.values.scanLeft(0.0)(_ + _).drop(1)

    val rangedMap = (dist.keys zip rangedProbs).toMap

    val item = dist.filter( x => rangedMap(x._1) >= p).keys.head

    item
  }

}

object CBAlgorithm extends LazyLogging {

}

case class CBAllParams(
  algorithm: CBAlgoParams)


case class CBAlgoParams(
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelContainer: Option[String] = None,
    modelName: String = "",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams


class SingleGroupTrainer(
    events: UsageEventDAO,
    users: UsersDAO,
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
      var result = cbAlgo.vw.learn(vwString)
/*      examples += 1
      if (examples % 20 == 0) { // save every 20 events
        log.info(s"Got result to vw.learn(usageEvent) of: ${result.toString}")
        log.info(s"Sending the pseudo example to save the model: save_${params.modelName}")
        result = cbAlgo.vw.learn(s" save_${result.toString} ") // pseudo example that tells VW to checkpoint the model
        log.info(s"Got result to save of: ${result.toString}")
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
    events: UsageEventDAO,
    users: UsersDAO,
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
    // (${user properties list} -- List("converted", "testGroupId")).fields.map { entry =>
    //   entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId
    // }.mkString(" ")

    //val vwString = classString + " |" +  resourceId + " " + // may need to make a namespace per group by resourceId+testGroupId
    val vwString = classString + " | " + // may need to make a namespace per group by resourceId+testGroupId
      rawTextToVWFormattedString(
        "user_" + userId + " " + "testGroupId_" + testGroupId + " " +
          user.propsToMapOfSeq.map { case(propId, propSeq) =>
            // propString is a flatmapped Seq of Strings separated by %, to make into a user feature, split, sort, and flatmap
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
