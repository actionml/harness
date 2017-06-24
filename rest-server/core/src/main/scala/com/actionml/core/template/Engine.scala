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
import cats.data.Validated.Valid
import com.actionml.core.validate.{JsonParser, ValidateError}
import com.actionml.core.backup.{FSMirroring, Mirroring}
import scaldi.{Injectable, Injector}

import com.typesafe.scalalogging.LazyLogging

import com.actionml.core.backup.FSMirroring

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  * TODO: not using injection for mirroring
  */
abstract class Engine(/*implicit inj: Injector*/) extends LazyLogging /*with Injectable*/ with JsonParser with FSMirroring {

  var engineId: String = _

  //private val mirroring: Mirroring = inject[Mirroring]

  def init(json: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[RequiredEngineParams](json).andThen { p =>
      engineId = p.engineId
      Valid(true)
    }
  }

  def initAndGet(json: String): Engine
  def destroy(): Unit
  def start(): Engine = {logger.trace(s"Starting base Engine with engineId:$engineId"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Engine with engineId:$engineId")}

  def train()
  def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
    mirrorJson(engineId, json)
    Valid(true)
  }
  def query(json: String): Validated[ValidateError, String]
  def status(): String = "Does not support status message."

  // Slava, not sure why this is needed?
  // protected def inputInternal(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean]
}

trait EngineParams
trait QueryResult
trait Query

case class RequiredEngineParams(
  engineId: String, // required, resourceId for engine
  engineFactory: String, // required to create the engine using engine.initAndGet(json: String)
  mirrorType: Option[String], // "hdfs" | "localfs",
  mirrorLocation: Option[String] // "path-or-descriptor-or-root-location"
) extends EngineParams
