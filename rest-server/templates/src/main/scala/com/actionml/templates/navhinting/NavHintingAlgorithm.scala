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

package com.actionml.templates.navhinting

import akka.actor._
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage._
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError}
import com.typesafe.scalalogging.LazyLogging

/** Creates an Actor to train for each input. The Actor works in a thread and when complete will accept any
  * new training request and start anew. In parallel queries can be made of the existing model. When training
  * is done, the live model is updated atomically. This is therefore a Kappa style learner, that is eventually
  * consistent with all input. The difference between real time and eventually is arbitrary and based on compute
  * power. All input is persisted before the model is calculated to guarantee the model can be recreated. We may
  * be able to train and persist in parallel when we switch to the new async DB client.
  */
class NavHintingAlgorithm(dataset: NavHintingDataset)
  extends Algorithm[NHQuery, NHQueryResult] with KappaAlgorithm[NavHintingAlgoInput] with JsonParser with Mongo {

  val serverHome = sys.env("HARNESS_HOME")

  private val actors = ActorSystem("NavHintingAlgorithm") // todo: should this be derived from the classname? or is
  // it engine instance specific

  var trainer: Option[ActorRef] = None
  var params: NHAlgoParams = _
  var resourceId: String = _
  var model: NavHintingModel = _

  var convertedJourneys: Map[String, Trail] = Map.empty

  // from the Dataset determine which groups are defined and start training on them

  var events = 0

  override def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    //val response = parseAndValidate[ScaffoldAlgoParams](json)
    resourceId = rsrcId
    parseAndValidate[NHAllParams](json).andThen { p =>
      params = p.algorithm.copy()
      // init the in-memory model, from which predicitons will be made and to which new conversion Journeys will be added
      dataset.completedJourneysDAO.find(allCollectionObjects).foreach { j =>
        convertedJourneys += (j._id -> j.trail)
      }
      // TODO: create model from conversion Trails
      // trigger Train and wait for finish, then input will trigger Train and Query will us the latest trained model
      model = new NavHintingModel()
      trainer = Some(new NavHintTrainer(convertedJourneys, params, resourceId, model, this).self)
      // TODO: should train and wait for completion
      Valid(true)
    }
  }

  override def input(datum: NavHintingAlgoInput): Validated[ValidateError, Boolean] = {
    try {
      logger.trace(s"Train Nav Hinting Model with datum: $datum")
      trainer.get ! Train(datum)
      Valid(true)
    } catch {
      case e: NoSuchElementException =>
        logger.error(s"Nav Hinting Train triggered on uninitialized training Actor, waiting for initialization.")
        Invalid(ValidRequestExecutionError(s"Nav Hinting Train triggered on uninitialized training Actor, waiting for initialization."))
    }
  }

  override def predict(query: NHQuery): NHQueryResult = {
    // todo: isDefinedAt is not enough to know there have been events
    // if(dataset.navEventsDAO isDefinedAt query.eligibleNavIds) getVariant(query) else getDefaultVariant(query)
    // TODO: stub, implement
    NHQueryResult(Array.empty[String])
  }

  override def destroy(): Unit = {
    // remove old model since it is recreated with each new NavHintingEngine
  }


  override def stop(): Unit = {
    actors.terminate().wait()
  }

}

object NavHintingAlgorithm extends LazyLogging {

}

case class NavHintingAlgoInput(
  user: User,
  event: NHNavEvent,
  resourceId: String )
  extends AlgorithmInput

case class Train(datum: NavHintingAlgoInput)

case class NHAllParams(
  algorithm: NHAlgoParams)


/*
  "algorithm":{
    "numQueueEvents": 50,
    "decayFunction": "clicks",
    "halfLifeDecayLambda": 1.0,
    "num": 1
  }

*/
case class NHAlgoParams(
    numQueueEvents: Int =  50,
    decayFunction: String = "clicks",
    halfLifeDecayLambda: Float = 1.0f,
    num: Int = 1)
  extends AlgorithmParams


class NavHintTrainer(
    convertedJourneys: Map[String, Trail],
    params: NHAlgoParams,
    resourceId: String,
    model: NavHintingModel,
    nhAlgo: NavHintingAlgorithm)
  extends ActorWithLogging {

  override def receive: Receive = {
    case t: Train ⇒
      log.debug(s"$name Receive 'Train', run group training")
      train(t.datum)
  }

  private def train(input: NavHintingAlgoInput): Unit = {
    log.debug(s"$name Start work")
    // TODO: take the journeys, sum weights as vecotrs, create a "model" of sorted sum weights
    // this will yield a vector of ids ordered by weight, from which we will pull the highest few (one?)\
    // given the decay functions, this will make the weight a Double
    // TODO: should we have a way to suck up all queued input?
  }

}

object NavHintTrainer {

  def props(
    convertedJourneys: Map[String, Trail],
    users: UsersDAO,
    params: NHAlgoParams,
    resourceId: String,
    model: NavHintingModel,
    nhAlgo: NavHintingAlgorithm): Props = Props(new NavHintTrainer(convertedJourneys , params, resourceId, model, nhAlgo))

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
