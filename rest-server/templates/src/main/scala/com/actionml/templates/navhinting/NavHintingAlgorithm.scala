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
import com.actionml.core.validate.{JsonParser, ParseError, ValidRequestExecutionError, ValidateError}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import salat.dao.SalatDAO

import scala.concurrent.Future
import scala.math._
import scala.concurrent.ExecutionContext.Implicits.global


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

  //private val actors: ActorSystem = ActorSystem(dataset.resourceId)

  var params: NHAlgoParams = _
  val resourceId: String = dataset.resourceId
  var model: Map[String, Double] = Map.empty
  var activeJourneys: Map[String, Seq[(String, DateTime)]] = Map.empty // kept as key - user-id, sequence of nav-ids and timestamps

  override def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[NHAllParams](json).andThen { p =>
      if (DecayFunctionNames.All.contains(p.algorithm.decayFunction.getOrElse(DecayFunctionNames.ClickTimes))) {
        params = p.algorithm.copy()
        // init the in-memory model, from which predicitons will be made and to which new conversion Journeys will be added
        dataset.activeJourneysDAO.find(allCollectionObjects).foreach { j =>

          activeJourneys += (j._id -> j.trail)
        }
        // TODO: create model from conversion Trails
        // trigger Train and wait for finish, then input will trigger Train and Query will us the latest trained model
        model = dataset.navHintsDAO.findOne(allCollectionObjects).getOrElse(Hints(Map.empty)).hints
        //trainer = Some(new NavHintTrainer(convertedJourneys, params, resourceId, model, this).self)
        // TODO: should train and wait for completion
        Valid(true)
      } else { //bad decay function name
        Invalid(ParseError(s"Bad decayFunction: ${p.algorithm.decayFunction}"))
      }
    }
  }

  override def input(datum: NavHintingAlgoInput): Validated[ValidateError, Boolean] = {
      logger.trace(s"Train Nav Hinting Model with datum: $datum")
      //trainer.get ! Train(datum)
      val activeJourney = activeJourneys.get(datum.event.entityId)
      val converted = datum.event.properties.conversion.getOrElse(false)
      if (converted) { // update the model with the active journeys and remove it from active
        if (activeJourney.nonEmpty) { // have an active journey
          updateModel(Journey(datum.event.entityId, activeJourney.get), DateTime.parse(datum.event.eventTime))
          activeJourneys -= datum.event.entityId // remove once converted
        } else { // new event from this user, start a journey
          activeJourneys += datum.event.entityId -> Seq((datum.event.targetEntityId, DateTime.parse(datum.event.eventTime)))
        }
      } else { // no conversion so just update activeJourney
        if (activeJourney.nonEmpty) { // have an active journey so update
          activeJourneys += (datum.event.entityId -> updateTrail(
            datum.event.targetEntityId,
            DateTime.parse(datum.event.eventTime),
            activeJourney.get))
        } else { // no conversion, no journey, create a new one
          activeJourneys += datum.event.entityId -> Seq((datum.event.targetEntityId, DateTime.parse(datum.event.eventTime)))
        }
      }
      Valid(true)
  }

  /** add the event to the end of an active journey subject to length limits */
  def updateTrail(navId: String, timeStamp: DateTime, trail: Seq[(String, DateTime)]): Seq[(String, DateTime)] = {
    val newTrail = trail :+ (navId, timeStamp)
    newTrail.takeRight(params.numQueueEvents.getOrElse(50))
  }


  /** update the model with a Future for every input. This may cause Futures to accumulate */
  def updateModel(convertedJourney: Journey,  now: DateTime): Unit = {
    applyDecayFunction(convertedJourney, now).map { weightedVectors =>
      weightedVectors.foreach { case (navId, weight) =>
        if (model.contains(navId)){ // add this part of the new converted journey to the vector
          model += navId -> (model(navId) + weight)
        } else { // add a new vector element
          model += navId -> weight
        }
      }
    }
    writeModelFile()

  }

  def writeModelFile(): Unit = {

    Future {

    }
  }

  def applyDecayFunction(journey: Journey, now: DateTime): Future[Seq[(String, Double)]] = {
    val decayFunctionName = params.decayFunction.getOrElse("click-order")
    Future[Seq[(String, Double)]] {
      val weigthedVector = Seq[(String, Double)]()
      val len = journey.trail.length
      decayFunctionName match {
        case DecayFunctionNames.ClickOrder =>
          journey.trail.zipWithIndex.map { case((navId, timestamp), i) =>
            val reverseIndex = len - i
            val newWeight = 1d/reverseIndex
            navId -> newWeight
          }
        case DecayFunctionNames.ClickTimes =>
          journey.trail.map { case (navId, timestamp) =>
            val  millisFromConversion = now.getMillis - timestamp.getMillis
            val timeFromConversion = if (millisFromConversion == 0) 1 else millisFromConversion
            if (timeFromConversion < 0 ) {
              val debug = timeFromConversion
            }
            navId -> (1d/timeFromConversion)
          }
        case DecayFunctionNames.HalfLife =>
          journey.trail.map { case (navId, timestamp) =>
            val  millisFromConversion = now.getMillis - timestamp.getMillis
            val daysFromConversion = if (millisFromConversion == 0) 1 else millisFromConversion / 8.64e+7f
            val halfLifeDays = params.halfLifeDecayLambda.getOrElse(1f)
            navId -> pow(2.0d, -daysFromConversion / halfLifeDays)
          }
        case _ =>
          logger.warn(s"Invalid decay function in Engines JSON config file: $decayFunctionName")
          journey.trail.map { case (navId, timestamp) =>
            navId -> 0d
          }
      }
    }

  }


  override def predict(query: NHQuery): NHQueryResult = {
    // find model elements that match eligible and sort by weight, sort before taking the top k
    val results = (query.eligibleNavIds collect model zip query.eligibleNavIds) // reduce the model to only nav events with the same key as eligible events
      .map { case (v, k) => k -> v } // swap key and value
      .sortBy(-_._2) // sort by value, which is the score here, minus for
      .take(params.num.getOrElse(1))
    NHQueryResult(results)
  }

  override def destroy(): Unit = {
    // remove old model since it is recreated with each new NavHintingEngine
  }


  override def stop(): Unit = {
    // actors.terminate().wait()
  }

}


case class NavHintingAlgoInput(
  event: NHNavEvent,
  resourceId: String )
  extends AlgorithmInput

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
    numQueueEvents: Option[Int] = Some(50),
    decayFunction: Option[String] = Some("click-order"), // or click-times, or half-life
    halfLifeDecayLambda: Option[Float] = None,
    num: Option[Int] = Some(1))
  extends AlgorithmParams

object DecayFunctionNames {
  val ClickOrder = "click-order"
  val ClickTimes = "click-times"
  val HalfLife = "half-life"
  val All = List(ClickOrder, ClickTimes, HalfLife)
}

