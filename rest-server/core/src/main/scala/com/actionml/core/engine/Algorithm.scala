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

package com.actionml.core.engine

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.model.{Comment, Response}
import com.actionml.core.validate.{JsonSupport, NotImplemented, ValidateError}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

/**
  * Defines the API for Harness Algorithms, init/destroy are required, start/stop are optional.
  * Typically this class is not extended but either KappaAlgorithm, LambdaAlgorithm, or KappaLambdaAlgorithm,
  * which differ in how they get data and update models
  */
abstract class Algorithm[Q, R] extends LazyLogging with JsonSupport {
  var engineId: String = _
  var modelPath: String = _  // optional place in some filesystem to persist the model, a file path

  def init(engine: Engine): Validated[ValidateError, Response] = {
    engineId = engine.engineId
    modelPath = engine.modelContainer + engineId //todo: should the Engine even know about the modelContainer?
    Valid(Comment("Init processed"))
  }

  def destroy(): Unit

  // todo: removeOne these is not needed
  // def start(): Algorithm[Q, R] = {logger.trace(s"No-op starting base Algorithm"); this}
  // def stop(): Unit = {logger.trace(s"No-op stopping base Kappa/Lambda Algorithm")}

  def query(query: Q): Future[R]

  def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = {
    Invalid(NotImplemented(s"Can't cancel job $jobId [engineId $engineId]"))
  }
}

trait AlgorithmParams
trait AlgorithmQuery
trait QueryResult
