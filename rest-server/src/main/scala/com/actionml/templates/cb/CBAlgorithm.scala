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
import akka.dispatch.{BoundedPriorityMailbox, PriorityGenerator}
import com.actionml.core.storage.Mongo
import com.actionml.core.template.{AlgorithmParams, Algorithm}
import com.mongodb.casbah.MongoCollection
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration.Duration


/** Creates Actors for each group and does input event triggered training continually. The GroupTrain Actors
  * manager their own model persistence in true Kappa "micro-batch" style. Precessing typically small groups
  * of events when a new one is detected, then updating the model for that group for subsequent queries. The GroupTrain
  * Actors are managed by the CBAlgorithm and will be added and killed when needed.
  *
  * @param p All needed params for the CB lib as well as the dataset containing events and data used in training. The
  *          dataset will contain groups from which the GroupTrain Actors are created
  */
class CBAlgorithm(p: CBAlgoParams) extends Algorithm( new Mongo, p: CBAlgoParams ) {

  // from the Dataset determine which groups are defined and start training on them

  import context.dispatcher
  val ticker = context.system.scheduler.schedule(0 millis, 5 second, self, Start)

  implicit val system = ActorSystem(p.dataset.resourceId+"AKKA-SEED") // Todo: Semen is a unique id per template instance required?

  var trainers = p.dataset.CBCollections.usageEventGroups
    .map { case(groupName, mongoCollection) =>
      val trainer = context watch context.actorOf(Props[SingleGroupTrainer].withMailbox("custom-mailbox"), groupName)
      groupName -> trainer
  }

  def train(groupName: String): Unit = {
    trainers(groupName) !
  }

  def remove(groupName: String): Unit = {
    trainers = trainers - groupName
  }

  def add(groupName: String, mongoCollection: MongoCollection): Unit = {
    trainers = trainers + ( groupName -> new SingleGroupTrainer(mongoCollection) )
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
  import context.dispatcher

  override def receive: Receive = await

  def await: Receive = {
    case Run ⇒
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
    case Run ⇒
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
  case object Run
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

class CustomMailbox(settings: ActorSystem.Settings, config: Config) extends BoundedPriorityMailbox(
  PriorityGenerator {
    case SingleGroupTrainer.Run => 100
    case PoisonPill => 1
  }, capacity = 1, pushTimeOut = Duration("0 millis"))
