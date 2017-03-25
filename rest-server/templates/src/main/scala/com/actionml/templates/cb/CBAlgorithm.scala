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
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{Algorithm, AlgorithmParams}
import com.mongodb.casbah.MongoCollection

import scala.collection.mutable
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

  private val manager = system.actorOf(Props[TrainerManager], "TrainerManager")

  // from the Dataset determine which groups are defined and start training on them

  def init(): Unit = {
    val groups: Map[String, MongoCollection] = p.dataset.CBCollections.usageEventGroups
    logger.info(s"Init manager for ${groups.size} groups. ${groups.mkString(", ")}")
    manager ! TrainerManager.CreateTrainers(groups)
  }

  def train(groupName: String): Unit = {
    manager ! TrainerManager.Train(groupName)
  }

  def remove(groupName: String): Unit = {
    manager ! TrainerManager.Remove(groupName)
  }

  def add(groupName: String, collection: MongoCollection): Unit = {
    manager ! TrainerManager.Create(groupName, collection)
  }

  def stop() = {
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

class TrainerManager extends ActorWithLogging{

  val trainers = mutable.HashMap.empty[String, ActorRef]

  override def receive: Receive = {
    case TrainerManager.CreateTrainers(list) ⇒

      val exists = trainers.keys.toList
      val diff = list.filter(!exists.contains(_))

      log.info("Exists trainers: {}", exists)
      log.info("New trainers: {}", list)
      log.info("Diff trainers: {}", diff)

      diff.foreach { case (trainer, collection) ⇒
        val actor = context watch context.actorOf(SingleGroupTrainer.props(collection), trainer)
        trainers += trainer → actor
      }

    case TrainerManager.Create(trainer, collection) ⇒
      log.info("Create trainer {}", trainer)
      if (!trainers.contains(trainer)) {
        val actor = context watch context.actorOf(SingleGroupTrainer.props(collection), trainer)
        trainers += trainer → actor
      }

    case TrainerManager.Remove(trainer) ⇒
      log.info("Stop trainer {}", trainer)
      context stop trainers(trainer)

    case TrainerManager.Train(trainer) ⇒
      log.info("Train trainer {}", trainer)
      trainers(trainer) ! SingleGroupTrainer.Train

    case Terminated(actor) ⇒
      log.info("Remove trainer {}", actor.path.name)
      trainers -= actor.path.name
  }
}
object TrainerManager{
  case class CreateTrainers(trainers: Map[String, MongoCollection])
  case class Train(trainer: String)
  case class Create(trainer: String, collection: MongoCollection)
  case class Remove(trainer: String)
}


class SingleGroupTrainer(events: MongoCollection) extends ActorWithLogging {

  import SingleGroupTrainer._
  import context.dispatcher

  override def receive: Receive = await

  def await: Receive = {
    case Train ⇒
      log.info(s"${name} Receive 'Run', run job")
      log.info(s"${name} Switch context to work")
      context become work
      startWork() recover {
        case ex: Throwable ⇒ log.error(ex, "ERROR!")
      } map { _ ⇒
        log.info(s"${name} Work complete!")
        log.info(s"${name} Switch context to await")
        context become await
      }

  }

  def work: Receive = {
    case Train ⇒
      log.info(s"${name} Received 'Run', not react, because already started job")
  }

  private def startWork(): Future[Unit] = {
    log.info(s"${name} Start work")
    Future {
      Thread.sleep(15000)
    }
  }

}

object SingleGroupTrainer{

  case object Train

  def props(events: MongoCollection): Props = Props(new SingleGroupTrainer(events))
}

trait ActorWithLogging extends Actor with ActorLogging{

  val name = self.path.name

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"${Console.GREEN}Start actor ${name}${Console.RESET}")
  }

  override def postStop(): Unit = {
    super.postStop()
    log.info(s"${Console.RED}Stop actor ${name}${Console.RESET}")
  }

}
