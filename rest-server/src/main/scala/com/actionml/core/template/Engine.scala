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

import akka.http.scaladsl.model.StatusCode
import com.typesafe.scalalogging.LazyLogging

/**
  * Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E or a Seq[E] to input or inputCol of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  *
  * @param d dataset to store input
  * @param p engine params, typically for the algorithm
  * @tparam E input case class type, often and Event of some type
  */
abstract class Engine[E](d: Dataset[E], p: EngineParams) extends LazyLogging {

  val dataset = d
  val params = p

  def train()
  def input(json: String, trainNow: Boolean = true): StatusCode
  def query(json: String): (QueryResult, StatusCode)
}

trait EngineParams
trait QueryResult
trait Query
