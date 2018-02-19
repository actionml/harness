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

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.model.{AlgorithmParams, GenericEvent, GenericQuery, GenericQueryResult}
import com.actionml.core.model.{GenericQuery, GenericQueryResult}
import com.actionml.core.storage._
import com.actionml.core.template._
import com.actionml.core.validate.{JsonParser, ValidateError}

/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class ScaffoldAlgorithm(dataset: ScaffoldDataset)
  extends Algorithm[GenericQuery, GenericQueryResult] with KappaAlgorithm[GenericEvent] with JsonParser
    with Mongo {

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

  override def input(datum: GenericEvent): Validated[ValidateError, Boolean] = {
    // For Kappa the model update happens or it triggered with each input
    Valid(true)
  }


  def predict(query: GenericQuery): GenericQueryResult = {
    GenericQueryResult()
  }

  override def stop(): Unit = {
    // May want to send terminate signal to Actors and wait for completion
  }

}

case class AllParams(
  algorithm: ScaffoldAlgoParams)


case class ScaffoldAlgoParams(
    dummyParam: String) // since not an Option, this is required for this Engine
  extends AlgorithmParams

case class ScaffoldAlgorithmInput(
  engineId: String )
  extends AlgorithmInput

