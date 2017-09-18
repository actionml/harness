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

package com.actionml.templates.scaffold

import java.io.File
import java.util.concurrent.TimeoutException

import akka.actor._
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.storage._
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError}
import com.typesafe.scalalogging.LazyLogging
//import java.io.File
import java.nio.file.{Files, Paths}

import org.joda.time.{DateTime, Duration}
import vowpalWabbit.learner._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/** Scaffold for and Engine's Algorithm. Does only generic things. Use as a starting point for a new Engine */
case class ScaffoldAlgorithmInput(
    user: User,
    event: ScaffoldUsageEvent,
    resourceId: String )
  extends AlgorithmInput

case class Train(datum: ScaffoldAlgorithmInput)

/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  */
class ScaffoldAlgorithm[T <: ScaffoldAlgorithmInput](dataset: ScaffoldDataset)
  extends Algorithm with KappaAlgorithm[ScaffoldAlgorithmInput] with JsonParser with Mongo {

  override def init(json: String, rsrcId: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[AllParams](json).andThen { p =>
      // p is just the validated algo params from the engine's params json file.
      Valid(true)
    }
  }

  override def destroy(): Unit = {
    // remove old model since it is recreated with each new ScaffoldEngine
    // may want to await completetion of a Future here in a "try" if destroy may take time.
    /*
    try{ Await.result(deleteModel, 2 seconds) } catch {
      case e: TimeoutException =>
        logger.error(s"Error unable to delete the VW model file for $resourceId at $modelPath in the 2 second timeout.")
    }
    */

  }

  override def input(datum: ScaffoldAlgorithmInput): Validated[ValidateError, Boolean] = {
    // For Kappa the model update happens or it triggered with each input
    Valid(true)
  }


  def predict(query: CBQuery): CBQueryResult = {

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
case class AllParams(
  algorithm: ScaffoldAlgoParams)


case class ScaffoldAlgoParams(
    dummy: String = "un-used example parameter")
  extends AlgorithmParams

