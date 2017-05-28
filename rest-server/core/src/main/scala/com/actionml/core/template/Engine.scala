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

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  */
abstract class Engine extends LazyLogging {

  // Todo: not sure how to require a val dataset: Dataset, which takes a type of Event parameter Dataset[CBEvent]
  // for instance. Because each Dataset may have a different parameter type
  // val params: EngineParams
  val algo: Algorithm
  var engineId: String = _

  def init(json: String): Validated[ValidateError, Boolean]
  def initAndGet(json: String): Engine
  def destroy(): Unit
  def start(): Engine = {logger.trace(s"Starting base Engine with engineId:$engineId"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Engine with engineId:$engineId")}

  def train()
  def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean]
  def query(json: String): Validated[ValidateError, String]
}

trait EngineParams
trait QueryResult
trait Query
