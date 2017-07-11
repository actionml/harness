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
import com.actionml.core.template.{Query, Algorithm, AlgorithmParams}
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.{MongoDBObject, TypeImports}
import org.joda.time.{Duration, DateTime}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, JValue, MappingException}
import org.slf4j.event.SubstituteLoggingEvent

import scala.concurrent.Future
import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Paths}

import vw.VW

import scala.reflect.io.File

/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the CBAlgorithm and will be added and killed when needed.
  *
  */
class CBAlgorithm(dataset: CBDataset) extends Algorithm with JsonParser with Mongo {

  private val actors = ActorSystem("CBAlgorithm") // todo: should this be derived from the classname?
  var trainers = Map.empty[String, ActorRef]
  var params: CBAlgoParams = _
  var resourceId: String = _

  // from the Dataset determine which groups are defined and start training on them

  def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    //val response = parseAndValidate[CBAlgoParams](json)
    resourceId = rsrcId
    parseAndValidate[CBAllParams](json).andThen { p =>
      params = p.algorithm
      // todo: remove old model since it is recreated with each new input batch, not sure how to do this in vw
      val groupEvents: Map[String, UsageEventDAO] = dataset.usageEventGroups
      logger.trace(s"Init algorithm for ${groupEvents.size} groups. ${groupEvents.mkString(", ")}")
      val exists = trainers.keys.toList
      val diff = groupEvents.filterNot { case (key, _) =>
        exists.contains(key) && dataset.GroupsDAO.findOne(DBObject("groupId" -> key)).nonEmpty
      }

      diff.foreach { case (trainer, collection) =>
        val group = dataset.GroupsDAO.findOne(DBObject("groupId" -> trainer)).get
        val actor = actors.actorOf(SingleGroupTrainer.props(collection, dataset.usersDAO, params, group, resourceId), trainer)
        trainers += trainer → actor
      }
      Valid(true)
    }
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
          resourceId),
        groupName)
      trainers += groupName → actor
    }
  }

  def predict(query: CBQuery): CBQueryResult = {
    if(dataset.usageEventGroups isDefinedAt query.groupId) getVariant(query) else getDefaultVariant(query)
  }

  override def stop(): Unit = {
    actors.terminate().wait()
  }

  def getVariant(query: CBQuery): CBQueryResult = {
    val vw = new VW(" -i " + params.modelName)

    val group = dataset.GroupsDAO.findOneById(query.groupId).get

    val numClasses = group.pageVariants.size

    val classString = (1 to numClasses).mkString(" ")

    val users = dataset.usersDAO.find(allCollectionObjects).seq.toSeq.map(user => user._id -> user).toMap

    val queryText = SingleGroupTrainer.constructVWString(classString, query.user, query.groupId, users, resourceId)

    val pred = vw.predict(queryText).toInt
    vw.close()

    //see http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.109.4518&rep=rep1&type=pdf
    //we use testPeriods as epsilon0

    val startTime = group.testPeriodStart
    val endTime = group.testPeriodEnd.getOrElse(new DateTime())

    val maxEpsilon = 1.0 - (1.0/numClasses)
    val currentTestDuration = new Duration(startTime, new DateTime()).getStandardMinutes().toDouble
    val totalTestDuration = new Duration(startTime, endTime).getStandardMinutes().toDouble

    //scale epsilonT to the range 0.0-maxEpsilon
    val epsilonT = scala.math.max(0, scala.math.min(maxEpsilon, maxEpsilon * (1.0 - currentTestDuration/ totalTestDuration) ))

    //val testGroupMap = model.classes.classes(query.groupId).toMap
    // todo: wrong, the map should be Map[Int, String] but not sure where the Int comes from, index of the variants?
    val groupMap = group.pageVariants.indices.zip(group.pageVariants).toMap

    val probabilityMap = groupMap.keys.map { x => x -> (if(x == pred) 1.0 - epsilonT else epsilonT / (numClasses - 1.0) ) }.toMap

    val sampledPred = sample(probabilityMap)

    val pageVariant = groupMap(sampledPred)
    CBQueryResult(pageVariant, groupId = query.groupId)
  }


  def getDefaultVariant(query: CBQuery): CBQueryResult = {
    logger.info("Test group has no training data with  yet. Fall back to uniform distribution")
    val group = dataset.GroupsDAO.findOneById(query.groupId).get

    val variants = group.pageVariants
    val numClasses = variants.size
    val map = variants.map{ x => x -> 1.0/numClasses}.toMap
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

case class CBAllParams(
  algorithm: CBAlgoParams
)


case class CBAlgoParams(
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelName: String = "/Users/pat/harness/model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams


class SingleGroupTrainer(events: UsageEventDAO, users: UsersDAO, params: CBAlgoParams, group: GroupParams, resourceId: String)
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
    implicit val vw = createVW()

    eventsToVWStrings(
      events.find(allCollectionObjects).seq.toSeq,
      group.pageVariants,
      users.find(allCollectionObjects).seq.toSeq.map(user => user._id -> user).toMap,
      resourceId)
    .foreach(vw.learn(_))

    vw.close()
    log.debug(s"$name Finish work")
  }

  private def createVW(): VW = {
    val reg = "--l2 " + params.regParam
    val iters = "-c -k --passes " + params.maxIter
    val lrate = "-l " + params.stepSize

    val vw = new VW("--csoaa 10 " + "-b " + params.bitPrecision + " " + "-f " + params.modelName + " " + reg +
      " " + lrate + " " + iters)
    println(vw)
    vw
  }

  def eventsToVWStrings(
    events: Seq[UsageEvent],
    classes: Seq[String],
    users: Map[String, User],
    resourceId: String): Seq[String] = {

    events.map { event =>
      //val testGroupClasses = classes.getOrElse(example.testGroupId, Seq[(Int, String)]())

      //The magic numbers here are costs: 0.0 in case we see this variant, and it converted, 2.0 if we see it and it didn't convert, and 1.0 if we didn't see it, which is never the case since we train per group now, not all groups.
      val classString: String = classes.map { _ + ":" +
        //(if (thisClass._2 == example.variant && example.converted) "0.0"
          (if (event.converted) "0.0"
          else "2.0")
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
    resourceId: String):Props = Props(new SingleGroupTrainer(events, users, params, group, resourceId))

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

    val vwString = classString + " |" +  resourceId + " " + // may need to make a namespace per group by resourceId+testGroupId
      rawTextToVWFormattedString(
        "user_" + userId + " " + "testGroupId_" + testGroupId + " " +
          users.getOrElse(userId, User(userId, Map[String, String]())).properties.map { case(propId, propstring) =>
            propId + "_" + propstring.replaceAll("\\s+","_") + "_" + testGroupId
          }.mkString(" "))
    //log.info(s"VW string for training: $vwString")
    vwString
  }

  def rawTextToVWFormattedString(str: String) : String = {
    //VW input cannot contain these characters
    str.replaceAll("[|:]", "")
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
