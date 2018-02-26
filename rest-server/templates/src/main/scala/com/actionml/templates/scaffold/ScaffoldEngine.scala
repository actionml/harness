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
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.core.drawInfo
import com.actionml.core.model._
import com.actionml.core.template.Engine
import com.actionml.core.validate.{JsonParser, ValidateError}
import scaldi.{Injector, Module}

import scala.concurrent.{ExecutionContext, Future}

/** This is an empty scaffolding Template for an Engine that does only generic things.
  * This is not the minimal Template because many methods are implemented generically in the
  * base classes but is better used as a starting point for new Engines.
  */
class ScaffoldEngine(override implicit val injector: Module) extends Engine with JsonParser {

  var dataset: ScaffoldDataset = _
  var algo: ScaffoldAlgorithm = _
  var params: GenericEngineParams = _

  /** Initializing the Engine sets up all needed objects */
  override def init(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    super.init(json).flatMap { _ =>
      parseAndValidate[GenericEngineParams](json).andThen { p =>
        params = p
        engineId = params.engineId
        dataset = new ScaffoldDataset(engineId)
        algo = new ScaffoldAlgorithm(dataset)
        drawInfo("Generic Scaffold Engine", Seq(
          ("════════════════════════════════════════", "══════════════════════════════════════"),
          ("EngineId: ", engineId),
          ("Mirror Type: ", params.mirrorType),
          ("Mirror Container: ", params.mirrorContainer)))

        Valid(p)
      }.fold(e => Future.successful(Invalid(e)), { p =>
        dataset.init(json).flatMap { _ =>
          algo.init(json, p.engineId)
        } //( _ => algo.init(json, engineId))
      })
    }
  }

  // Used starting Harness and adding new engines, persisted means initializing a pre-existing engine. Only called from
  // the administrator.
  // Todo: This method for re-init or new init needs to be refactored, seem ugly
  // Todo: should return null for bad init
  override def initAndGet(json: String)(implicit ec: ExecutionContext): Future[ScaffoldEngine] = {
    init(json).map { response =>
      if (response.isValid) {
        logger.trace(s"Initialized with JSON: $json")
        this
      } else {
        logger.error(s"Parse error with JSON: $json")
        null.asInstanceOf[ScaffoldEngine] // todo: ugly, replace
      }
    }
  }

  override def stop(): Unit = {
    logger.info(s"Waiting for ScaffoldAlgorithm for id: $engineId to terminate")
    algo.stop() // Todo: should have a timeout and do something on timeout here
  }

  override def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(this.params.toString)
  }

  override def destroy()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info(s"Dropping persisted data for id: $engineId")
    for {
      _ <- dataset.destroy()
      _ <- algo.destroy()
    } yield ()
  }

  /*
  override def train(): Unit = {
    logger.warn(s"Only used for Lambda style training")
  }
  */

  /** Triggers parse, validation, and persistence of event encoded in the json */
  override def input(json: String, trainNow: Boolean = true)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    super.init(json).flatMap { _ =>
      logger.trace("Got JSON body: " + json)
      // validation happens as the input goes to the dataset
      super.input(json, trainNow).flatMap { sv =>
        if (sv.isValid)
          dataset.input(json).map(_.andThen(process)).map(_ => Valid(true))
        else
          Future.successful(Valid(true)) // Some error like an ExecutionError in super.input happened
        // todo: pass back indication of deeper error
      }
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

  /** triggers parse, validation of the query then returns the result with HTTP Status Code */
  def query(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, String]] = {
    logger.trace(s"Got a query JSON string: $json")
    parseAndValidate[GenericQuery](json).fold(e => Future.successful(Invalid(e)), query =>
      // query ok if training group exists or group params are in the dataset
      algo.predict(query).map(x => Valid(x.toJson))
    )
  }

}

