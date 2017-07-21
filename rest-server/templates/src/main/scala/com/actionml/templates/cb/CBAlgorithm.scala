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

import akka.actor._
import akka.event.Logging
import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.storage._
import com.actionml.core.template.{Algorithm, AlgorithmParams, Query}
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.mongodb.casbah.Imports._
import salat.global._
import com.typesafe.scalalogging.LazyLogging

import scala.tools.nsc.classpath.FileUtils
//import java.io.File
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.{MongoDBObject, TypeImports}
import org.joda.time.{DateTime, Duration}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, JValue, MappingException}
import org.slf4j.event.SubstituteLoggingEvent

import scala.concurrent.Future
import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.ConfigFactory
import vw.VW

import scala.reflect.io.File

/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the CBAlgorithm and will be added and killed when needed.
  *
  */
class CBAlgorithm(dataset: CBDataset) extends Algorithm with JsonParser with Mongo {

  val serverHome = sys.env("HARNESS_HOME")

  private val actors = ActorSystem("CBAlgorithm") // todo: should this be derived from the classname?
  var trainers = Map.empty[String, ActorRef]
  var params: CBAlgoParams = _
  var resourceId: String = _
  var modelPath: String = _

  // from the Dataset determine which groups are defined and start training on them

  var vw: VW = _

  def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    //val response = parseAndValidate[CBAlgoParams](json)
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
      groupEvents.foreach( groupName => add(groupName._1))
      createVW(params)//.close() // sets up the parameters for the model and names the file for storage of the
      // params .close() should write to the file

      Valid(true)
    }
  }

  def createVW(params: CBAlgoParams): VW = {
    val reg = " --l2 " + params.regParam + " "
    val iters = " -c -k --passes " + params.maxIter + " "
    val lrate = " -l " + params.stepSize + " "
    val cacheFile = s" --cache_file ${params.modelName}_cache "
    val checkpointing = " --save_resume "

    val config = " --csoaa 10 " + " -b " + params.bitPrecision + " -f " + params.modelName + reg +
      lrate + iters + cacheFile

    logger.info(s"VW: config: \n$config\n")
    vw = new VW(config)
    vw
  }

  override def destroy(): Unit = {
    // remove old model since it is recreated with each new CBEngine
    Files.deleteIfExists(Paths.get(modelPath))
    if(Files.deleteIfExists(Paths.get(modelPath))) logger.info(s"Unable to delete old VW model file: ${modelPath}")
    else logger.info(s"Unable to delete old VW model file: ${modelPath}")
    if (vw != null.asInstanceOf[VW]) vw.close()

  }

  def train(groupName: String): Unit = {
    try {
      logger.trace("Train trainer {}", groupName)
      trainers(groupName) ! SingleGroupTrainer.Train
    } catch{
      case e: NoSuchElementException =>
        logger.error(s"Training triggered on non-existent group: $groupName The group must be initialized first. " +
          s"All events for this group will be ignored. ")
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
          vw),
        groupName)
      trainers += groupName → actor
    }
  }

  def predict(query: CBQuery): CBQueryResult = { // todo: isDefinedAt is not enough to know there have been events
    if(dataset.usageEventGroups isDefinedAt query.groupId) getVariant(query) else getDefaultVariant(query)
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

    val users = dataset.usersDAO.find(allCollectionObjects).seq.toSeq.map(user => user._id -> user).toMap

    val queryText = SingleGroupTrainer.constructVWString(classString, query.user, query.groupId, users, resourceId)


    logger.info(s"Query string to VW: \n$queryText")
    val pred = vw.predict(queryText).toInt
    logger.info(s"VW: raw result, not yet run through probability distribution: ${pred}\n\n")
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
    //val pageVariant = groupMap(pred)
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
  algorithm: CBAlgoParams
)


case class CBAlgoParams(
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelContainer: Option[String] = None,
    modelName: String = "/Users/pat/harness/model.vw",
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
    vw: VW)
  extends ActorWithLogging {

  import SingleGroupTrainer._
  import com.actionml.core.storage.allCollectionObjects

  override def receive: Receive = {
    case Train ⇒
      log.debug(s"$name Receive 'Train', run group training")
      startWork()
  }

  private def startWork(): Unit = {
    log.debug(s"$name Start work")
    val inputs: Seq[String] = eventsToVWStrings(
      events.find(allCollectionObjects).limit(10000).toList, // Todo: do one event at a time or use a cursor here
      group.pageVariants.map( a => a._1.toInt -> a._2),
      users.find(allCollectionObjects).limit(10000).toList.map(user => user._id -> user).toMap,
      resourceId)

    log.info(s"VW input after escaping:\n")

    //for ( item <- inputs ) log.info(s"$item\n")
    for ( item <- inputs ) yield vw.learn(item)

    // vw.close()
    //CBAlgorithm.closeVW()
    log.debug(s"$name Finish work")
  }


  def eventsToVWStrings(
    events: Seq[UsageEvent],
    variants: Map[Int, String],
    users: Map[String, User],
    resourceId: String): Seq[String] = {

    events.map { event =>
      //val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())

      //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it, which is never the case since we train per group now, not all groups.
      val classString: String = variants.map { case( variantIntKey, variantLable) =>
        variantIntKey.toString + ":" +
        //(if (thisClass._2 == example.variant && example.converted) "0.0"
          (if (variantLable == event.itemId && event.converted) "0.0"
          else if (variantLable == event.itemId ) "2.0"
          else "1.0")
      }.mkString(" ")

      constructVWString(classString, event.userId, event.testGroupId, users, resourceId)
    }

  }


}


object SingleGroupTrainer {

  case object Train

  def props(
    events: UsageEventDAO,
    users: UsersDAO,
    params: CBAlgoParams,
    group: GroupParams,
    resourceId: String,
    modelPath: String,
    vw: VW):Props = Props(new SingleGroupTrainer(events, users, params, group, resourceId, modelPath, vw))

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
    users: Map[String, User],
    resourceId: String): String = {

    @transient implicit lazy val formats = org.json4s.DefaultFormats

    // class-id|namespace user_ user testGroupId_ testGroupId
    // (${user properties list} -- List("converted", "testGroupId")).fields.map { entry =>
    //   entry._1 + "_" + entry._2.extract[String].replaceAll("\\s+","_") + "_" + testGroupId
    // }.mkString(" ")

    val props = users.getOrElse(userId, User(userId, Map[String, String]()))

    //val vwString = classString + " |" +  resourceId + " " + // may need to make a namespace per group by resourceId+testGroupId
    val vwString = classString + " | " + // may need to make a namespace per group by resourceId+testGroupId
      rawTextToVWFormattedString(
        "user_" + userId + " " + "testGroupId_" + testGroupId + " " +
          users.getOrElse(userId, User(userId, Map[String, String]())).properties.map { case(propId, propstring) =>
            // propString is a flatmapped Seq of Strings separated by %, to make into a user feature, split, sort, and flatmap
            val props = propstring.split("%").map { prop =>
              propId + "_" + prop.replaceAll("\\s+", "_") + "_" + testGroupId
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
