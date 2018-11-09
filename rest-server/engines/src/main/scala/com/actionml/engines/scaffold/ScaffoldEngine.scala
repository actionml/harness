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
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.drawInfo
import com.actionml.core.model.{GenericEngineParams, GenericEvent, GenericQuery}
import com.actionml.core.engine._
import com.actionml.core.validate.{JsonParser, ValidRequestExecutionError, ValidateError, WrongParams}


/** This is an empty scaffolding Template for an Engine that does only generic things.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class ScaffoldEngine extends Engine with JsonParser {

  var dataset: ScaffoldDataset = _
  var algo: ScaffoldAlgorithm = _
  var params: GenericEngineParams = _

  /** Initializing the Engine sets up all needed objects */
  override def init(json: String, deepInit: Boolean = true): Validated[ValidateError, String] = {
    super.init(json).andThen { _ =>
      parseAndValidate[GenericEngineParams](json).andThen { p =>
        params = p
        engineId = params.engineId
        dataset = new ScaffoldDataset(engineId)
        algo = new ScaffoldAlgorithm(json, dataset)
        drawInfo("Generic Scaffold Engine", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("EngineId: ", engineId),
          ("Mirror Type: ", params.mirrorType),
          ("Mirror Container: ", params.mirrorContainer)))

        Valid(p)
      }.andThen { p =>
        dataset.init(json).andThen { r =>
          if (deepInit) algo.init(this) else Valid(jsonComment("ScaffoldAlgorithm updated"))
        }
      }
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  // Todo: should return null for bad init
  override def initAndGet(json: String): ScaffoldEngine = {
    val response = init(json)
    if (response.isValid) {
      logger.trace(s"Initialized with JSON: $json")
      this
    } else {
      logger.error(s"Parse error with JSON: $json")
      null.asInstanceOf[ScaffoldEngine] // todo: ugly, replace
    }
  }

  override def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(this.params.toString)
  }

  override def destroy(): Unit = {
    logger.info(s"Dropping persisted data for id: $engineId")
    dataset.destroy()
    algo.destroy()
  }

  /*
  override def train(): Unit = {
    logger.warn(s"Only used for Lambda style training")
  }
  */

  /** Triggers parse, validation, and persistence of event encoded in the json */
  override def input(json: String): Validated[ValidateError, String] = {
    super.init(json).andThen { _ =>
      logger.trace("Got JSON body: " + json)
      // validation happens as the input goes to the dataset
      if (super.input(json).isValid)
        dataset.input(json).andThen(process).map(_ => jsonComment("ScaffoldEngine input processed"))
      else
        Invalid(ValidRequestExecutionError(jsonComment("Some error like an ExecutionError in super.input happened")))
      // todo: pass back indication of deeper error
    }
  }

  /** Triggers Algorithm processes. We can assume the event is fully validated and transformed into
    * whatever specific event the json represented. Now we can process it by it's type */
  def process(event: GenericEvent): Validated[ValidateError, GenericEvent] = {
    event match {
      // Here is where you process by derivative type
      case _ =>
    }
    Valid(event)
  }

  override def train(): Validated[ValidateError, String] = {
    logger.info("got to Scaffold.train")
    Valid(
      """
        |{
        |  "comment": "Training requested of the ScaffoldEngine"
        |  "jobId": "A fake job id"
        |}
      """.stripMargin
    )
  }

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String): Validated[ValidateError, String] = {
    logger.trace(s"Got a query JSON string: $json")
    parseAndValidate[GenericQuery](json).andThen { query =>
      // query ok if training group exists or group params are in the dataset
      val result = algo.query(query)
      Valid(result.toJson)
    }
  }

}

object ScaffoldEngine {
  def apply(json: String): ScaffoldEngine = {
    val engine = new ScaffoldEngine()
    engine.initAndGet(json)
  }

  // in case we don't want to use "apply", which is magically connected to the class's constructor
  def createEngine(json: String) = apply(json)
}
