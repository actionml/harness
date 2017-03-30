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
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Algorithm, AlgorithmParams}
import com.mongodb.casbah.MongoCollection
import org.slf4j.event.SubstituteLoggingEvent

import scala.concurrent.Future


/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries.
  * The GroupTrain Actors are managed by the CBAlgorithm and will be added and killed when needed.
  *
  * @param p All needed params for the CB lib as well as the dataset containing events and data used in training.
  *          The dataset will contain groups from which the GroupTrain Actors are created
  */
class CBAlgorithm(p: CBAlgoParams) extends Algorithm(new Mongo, p: CBAlgoParams) {

  private val system = ActorSystem("CBAlgorithm")

  private var trainers = Map.empty[String, ActorRef]

  // from the Dataset determine which groups are defined and start training on them

  def init(): CBAlgorithm = {
    val groups: Map[String, MongoCollection] = p.dataset.CBCollections.usageEventGroups
    logger.trace(s"Init manager for ${groups.size} groups. ${groups.mkString(", ")}")
    val exists = trainers.keys.toList
    val diff = groups.filterNot { case (key, _) ⇒
      exists.contains(key)
    }

    // todo: Semen, not sure of the purpose of this
    logger.trace("Existing trainers: {}", exists)
    logger.trace("New trainers: {}", groups)
    logger.trace("Diff trainers: {}", diff)

    diff.foreach { case (trainer, collection) ⇒
      val actor = system.actorOf(SingleGroupTrainer.props(collection), trainer)
      trainers += trainer → actor
    }
    this
  }

  def train(groupName: String): Unit = {
    logger.trace("Train trainer {}", groupName)
    trainers(groupName) ! SingleGroupTrainer.Train
  }

  def remove(groupName: String): Unit = {
    logger.info("Stop trainer {}", groupName)
    system stop trainers(groupName)
    logger.info("Remove trainer {}", groupName)
    trainers -= groupName
  }

  def add(groupName: String, collection: MongoCollection): Unit = {
    logger.info("Create trainer {}", groupName)
    if (!trainers.contains(groupName)) {
      val actor = system.actorOf(SingleGroupTrainer.props(collection), groupName)
      trainers += groupName → actor
    }
  }

  def stop(): Future[Terminated] = {
    system.terminate()
  }

}

case class CBAlgoParams(
    dataset: CBDataset, // required, where to get data
    maxIter: Int = 100, // the rest of these are VW params
    regParam: Double = 0.0,
    stepSize: Double = 0.1,
    bitPrecision: Int = 24,
    modelName: String = "model.vw",
    namespace: String = "n",
    maxClasses: Int = 3)
  extends AlgorithmParams


class SingleGroupTrainer(events: MongoCollection) extends ActorWithLogging {

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

}

object SingleGroupTrainer{

  case object Train

  def props(events: MongoCollection): Props = Props(new SingleGroupTrainer(events))
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
