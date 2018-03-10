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
import java.io.{FileInputStream, FileNotFoundException, FileOutputStream, IOException}
import java.nio.file.Paths

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

  //private val actors: ActorSystem = ActorSystem(dataset.engineId)

  var numUpdates = 0

  var params: NHAlgoParams = _
  //var model: Map[String, Double] = Map.empty // leave model in DB in case parallel engines are required
  var activeJourneys: Map[String, Seq[JourneyStep]] = Map.empty // kept as key - user-id, sequence of nav-ids and timestamps

  override def init(json: String, engine: Engine): Validated[ValidateError, Boolean] = {
    super.init(json, engine).andThen { _ =>
      parseAndValidate[NHAllParams](json).andThen { p =>
        if (DecayFunctionNames.All.contains(p.algorithm.decayFunction.getOrElse(DecayFunctionNames.ClickTimes))) {
          params = p.algorithm.copy()
          // init the in-memory model, from which predicitons will be made and to which new conversion Journeys will be added
          /* dataset.activeJourneysDAO.find(allCollectionObjects).foreach { j =>

            activeJourneys += (j._id -> j.trail)
          }
          */
          // trigger Train and wait for finish, then input will trigger Train and Query will us the latest trained model
          // model = dataset.navHintsDAO.findOneById("1").getOrElse(Hints(hints = Map.empty)).hints
          Valid(true)
        } else { //bad decay function name
          Invalid(ParseError(s"Bad decayFunction: ${p.algorithm.decayFunction}"))
        }
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
          activeJourneys += datum.event.entityId -> Seq(JourneyStep(datum.event.targetEntityId, DateTime.parse(datum.event.eventTime)))
        }
      } else { // no conversion so just update activeJourney
        if (activeJourney.nonEmpty) { // have an active journey so update
          activeJourneys += (datum.event.entityId -> updateTrail(
            datum.event.targetEntityId,
            DateTime.parse(datum.event.eventTime),
            activeJourney.get))
        } else { // no conversion, no journey, create a new one
          activeJourneys += datum.event.entityId -> Seq(JourneyStep(datum.event.targetEntityId, DateTime.parse(datum.event.eventTime)))
        }
      }
      Valid(true)
  }

  /** add the event to the end of an active journey subject to length limits */
  def updateTrail(navId: String, timeStamp: DateTime, trail: Seq[JourneyStep]): Seq[JourneyStep] = {
    val newTrail = trail :+ JourneyStep(navId, timeStamp)
    newTrail.takeRight(params.numQueueEvents.getOrElse(50))
  }


  /** update the model with a Future for every input. This may cause Futures to accumulate */
  def updateModel(convertedJourney: Journey,  now: DateTime): Unit = {
    applyDecayFunction(convertedJourney, now).map { weightedVectors =>
      //Semigroup[Map[String, Double]].combine(model, weightedVectors.toMap)
      weightedVectors.foreach { case (_id, weight) =>
        val existingModelHint = dataset.navHintsDAO.findOneById(_id).getOrElse(NavHint(_id, 0d))
        val status = dataset.navHintsDAO.save(NavHint(_id, weight + existingModelHint.weight))
        val updatedWeight = weight + existingModelHint.weight
        logger.trace(s"Updated db model with nav hint _id: ${_id} weight: ${updatedWeight} status: ${status} ")
      }
    }
  }

  /* works if we save to a file, but using a db
  def persistModel(): Unit = {
    numUpdates += 1
    if (numUpdates % params.updatesPerModelWrite.getOrElse(10) == 0 ) { // time to persist, ideally every 10 updates
      if (canStartWriter) { // its done so start a new write future
        canStartWriter = false
        Future[Unit] {
          // write a temp model file and move to model file when done
          import java.nio.file.StandardCopyOption.REPLACE_EXISTING
          import java.nio.file.Files.{copy, move}
          import java.nio.file.Paths.get

          implicit def toPath (filename: String) = get(filename)

          var outTemp = None: Option[FileOutputStream]
          var inOldModel = None: Option[FileInputStream]
          var outBackupModel = None: Option[FileOutputStream]

          try {
            outTemp = Some(new FileOutputStream(modelPath + ".tmp")) // where to write
            outTemp.get.write(serialise(model)) // write latest model to tmp file
            copy(Paths.get(modelPath), Paths.get(modelPath + ".backup"), REPLACE_EXISTING)
            move(Paths.get(modelPath + ".tmp"), Paths.get(modelPath), REPLACE_EXISTING)
          } catch {
            case e: IOException =>
              logger.error("Error writing the model update, if this persists you are not writing the model " +
                "and you need to resolve the issue.", e)
            case e: FileNotFoundException =>
              logger.error("Error finding a writable location for the model or its backup.", e)
          } finally {
            logger.trace("Closing all file streams.")
            if (outTemp.isDefined) outTemp.get.close // others are closed by the function????
          }
        }.onComplete(_ => canStartWriter = true) // attach a callback to release the lock
      } // Future is still running so ignore this opportunity to write and try again later after more updates
    } // wait until its time to write
  }
  */

  /*
  def persistModel(): Unit = {
    numUpdates += 1
    if (numUpdates % params.updatesPerModelWrite.getOrElse(1) == 0 ) { // save after some number of updates of the in-memory model
      // write the model to a DB
      logger.info(s"About to Save MODEL")
      val status = dataset.navHintsDAO.save(Hints(hints = model))
      logger.info(s"Saved MODEL")
    } // wait until its time to write
  }
  */

  def applyDecayFunction(journey: Journey, now: DateTime): Future[Seq[(String, Double)]] = {
    val decayFunctionName = params.decayFunction.getOrElse("click-order")
    Future[Seq[(String, Double)]] {
      val weigthedVector = Seq[(String, Double)]()
      val len = journey.trail.length
      decayFunctionName match {
        case DecayFunctionNames.ClickOrder =>
          journey.trail.zipWithIndex.map { case(step, i) =>
            val reverseIndex = len - i
            val newWeight = 1d/reverseIndex
            step.navId -> newWeight
          }
        case DecayFunctionNames.ClickTimes =>
          journey.trail.map { case step =>
            val  millisFromConversion = now.getMillis - step.timeStamp.getMillis
            val timeFromConversion = if (millisFromConversion == 0) 1 else millisFromConversion
            if (timeFromConversion < 0 ) {
              val debug = timeFromConversion
            }
            step.navId -> (1d/timeFromConversion)
          }
        case DecayFunctionNames.HalfLife =>
          journey.trail.map { case step =>
            val  millisFromConversion = now.getMillis - step.timeStamp.getMillis
            val daysFromConversion = if (millisFromConversion == 0) 1 else millisFromConversion / 8.64e+7f
            val halfLifeDays = params.halfLifeDecayLambda.getOrElse(1f)
            step.navId -> pow(2.0d, -daysFromConversion / halfLifeDays)
          }
        case _ =>
          logger.warn(s"Invalid decay function in Engines JSON config file: $decayFunctionName")
          journey.trail.map { case step =>
            step.navId -> 0d
          }
      }
    }

  }


  override def predict(query: NHQuery): NHQueryResult = {
    // find model elements that match eligible and sort by weight, sort before taking the top k
    val results = query.eligibleNavIds.map((_,0d)).map { case (eligibleNavId, w) =>
      dataset.navHintsDAO.findOneById(eligibleNavId).getOrElse(NavHint(eligibleNavId, 0))
    }.filter(_.weight > 0).map { navHint => navHint._id -> navHint.weight } // swap key and value
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
    num: Option[Int] = Some(1),
    updatesPerModelWrite: Option[Int] = Some(1))
  extends AlgorithmParams

object DecayFunctionNames {
  val ClickOrder = "click-order"
  val ClickTimes = "click-times"
  val HalfLife = "half-life"
  val All = List(ClickOrder, ClickTimes, HalfLife)
}

