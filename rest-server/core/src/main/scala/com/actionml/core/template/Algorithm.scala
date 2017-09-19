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

package com.actionml.core.template

import cats.data.Validated
import com.actionml.core.validate.ValidateError
import com.typesafe.scalalogging.LazyLogging

/**
  * Defines the API for Harness Algorithms, init/destroy are required, start/stop are optional.
  * Typically this class is not extended but either KappaAlgorithm, LambdaAlgorithm, or KappaLambdaAlgorithm,
  * which differ in how they get data and update models
  */
abstract class Algorithm[Q, R] extends LazyLogging {
  def init(json: String, rsrcId: String): Validated[ValidateError, Boolean]
  def destroy(): Unit
  def start(): Algorithm[Q, R] = {logger.trace(s"No-op starting base KappaLambdaAlgorithm"); this}
  def stop(): Unit = {logger.trace(s"No-op stopping base KappaLambdaAlgorithm")}
  def predict(query: Q): R
}

trait AlgorithmParams
trait AlgorithmQuery
trait QueryResult
