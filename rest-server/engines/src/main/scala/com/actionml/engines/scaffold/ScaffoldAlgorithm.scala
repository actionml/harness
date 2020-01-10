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

package com.actionml.engines.scaffold

import cats.data.Validated
import cats.data.Validated.Valid
import com.actionml.core.engine._
import com.actionml.core.model.{AlgorithmParams => _, _}
import com.actionml.core.validate.{JsonSupport, ValidateError}

import scala.concurrent.Future

/** Scafolding for a Kappa Algorithm, change with KappaAlgorithm[T] to with LambdaAlgorithm[T] to switch to Lambda,
  * and mixing is allowed since they each just add either real time "input" or batch "train" methods. It is sometimes
  * possible to make use of real time input in a LambdaAlgorithm such as the Universal Recommender making real time
  * changes to item attributes directly in the model rather than waiting for a training task.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class ScaffoldAlgorithm(json: String, dataset: ScaffoldDataset)
  extends Algorithm[GenericQuery, GenericQueryResult] with KappaAlgorithm[GenericEvent] with JsonSupport {

  /** Be careful to call super.init(...) here to properly make some Engine values available in scope */
  override def init(engine: Engine): Validated[ValidateError, Response] = {
    super.init(engine).andThen { _ =>
      parseAndValidate[AllParams](json).andThen { p =>
        // p is just the validated algo params from the engine's params json file.
        Valid(Comment("ScaffoldAlgorithm initialized"))
      }
    }
  }

  override def destroy(): Unit = {
  }

  override def input(datum: GenericEvent): Validated[ValidateError, String] = {
    // For Kappa the model update happens or it triggered with each input
    Valid(jsonComment("ScaffoldAlgorithm input processed"))
  }


  def query(query: GenericQuery): Future[GenericQueryResult] = {
    Future.successful(GenericQueryResult())
  }

}

case class AllParams(
  algorithm: ScaffoldAlgoParams)


case class ScaffoldAlgoParams(
    dummy: String) // since not an Option, this is required for this Engine
  extends AlgorithmParams

case class ScaffoldAlgorithmInput(
  engineId: String )
  extends AlgorithmInput

