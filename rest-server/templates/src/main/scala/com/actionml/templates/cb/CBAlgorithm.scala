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
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Algorithm, AlgorithmParams}
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.{TypeImports, MongoDBObject}
import org.joda.time.DateTime
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, MappingException}
import org.slf4j.event.SubstituteLoggingEvent
import scala.concurrent.Future

import java.io.{ObjectOutputStream, FileOutputStream, ObjectInputStream, FileInputStream}
import java.nio.file.{Files, Paths}

//import vw.VW


/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the CBAlgorithm and will be added and killed when needed.
  *
  */
class CBAlgorithm(dataset: CBDataset) extends Algorithm with JsonParser with Mongo {

  private val actors = ActorSystem("CBAlgorithm") // todo: should this be derived from the classname?
  private var trainers = Map.empty[String, ActorRef]
  var params: CBAlgoParams = _

  // from the Dataset determine which groups are defined and start training on them

  def init(json: String): Validated[ValidateError, Boolean] = {
    val response = parseAndValidate[CBAlgoParams](json)

    if (response.isValid) {
      params = response.getOrElse(CBAlgoParams())

      val groups: Map[String, UsageEventDAO] = dataset.usageEventGroups
      logger.trace(s"Init manager for ${groups.size} groups. ${groups.mkString(", ")}")
      val exists = trainers.keys.toList
      val diff = groups.filterNot { case (key, _) =>
        exists.contains(key)
      }

      diff.foreach { case (trainer, collection) =>
        val actor = actors.actorOf(SingleGroupTrainer.props(collection), trainer)
        trainers += trainer → actor
      }
      Valid(true)
    } else response.map(_ => false)
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

  def add(groupName: String, collection: UsageEventDAO): Unit = {
    logger.info("Create trainer {}", groupName)
    if (!trainers.contains(groupName)) {
      val actor = actors.actorOf(SingleGroupTrainer.props(collection), groupName)
      trainers += groupName → actor
    }
  }

  override def stop(): Unit = { // Todo: Semen, do we have to return Future[Terminated]? What is the benefit?
    actors.terminate().wait() // Semen: I added the wait, not sure why we were returning a terminated Future
  }

}

object CBAlgorithm extends JsonParser {

  def parseAndValidateParams( json: String): Validated[ValidateError, CBAlgoParams] = {
    val params = parse(json).extract[CBAllParams]
    Valid(params.algorithm)
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
    modelName: String = "model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams


class SingleGroupTrainer(events: UsageEventDAO) extends ActorWithLogging {

  import SingleGroupTrainer._

  override def receive: Receive = {
    case Train ⇒
      log.info(s"$name Receive 'Train', run group training")
      startWork()
  }

  private def startWork(): Unit = {
    log.info(s"$name Start work")

    log.info(s"$name Finish work")
  }

  def makeVWString(dbobj: MongoDBObject): String = {

    ""
  }

}

object SingleGroupTrainer {

  case object Train

  def props(events: UsageEventDAO): Props = Props(new SingleGroupTrainer(events))
}

trait ActorWithLogging extends Actor with ActorLogging{

  protected val name: String = self.path.name

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"${Console.GREEN}Start actor $name${Console.RESET}")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"${Console.RED}Stop actor $name${Console.RESET}")
  }

}
