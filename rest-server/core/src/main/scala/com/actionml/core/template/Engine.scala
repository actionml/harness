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
import com.actionml.core.backup.Mirroring
import scaldi.{Injectable, Injector}

import com.typesafe.scalalogging.LazyLogging

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  */
abstract class Engine(implicit inj: Injector) extends LazyLogging with Injectable with JsonParser {

  // Todo: not sure how to require a val dataset: Dataset, which takes a type of Event parameter Dataset[CBEvent]
  // for instance. Because each Dataset may have a different parameter type
  val params: EngineParams
  val algo: Algorithm
  val engineId: String

  private val mirroring: Mirroring = inject[Mirroring]

  // Slava: not sure if these should be here or in the mirroring Trait or the specific Object injected
  var mirrorType: Option[String] = _
  var mirrorLocation: Option[String] = _

  def init(json: String): Validated[ValidateError, Boolean] = {
    parseAndValidate[RequiredEngineParams](json).andThen { p =>
      engineId = p.engineId

      // Slava use something like this to initialize the mirroring, this should get the param out of the engine.json
      // no type defaults to localfs, no location will use the default location
      mirrorType = p.mirrorType
      mirrorLocation = p.mirrorLocation
      Valid(true)
    }
  }

  def initAndGet(json: String): Engine
  def destroy(): Unit
  def start(): Engine = {logger.trace(s"Starting base Engine with engineId:$engineId"); this}
  def stop(): Unit = {logger.trace(s"Stopping base Engine with engineId:$engineId")}

  def train()
  def input(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean] = {
    mirroring.mirrorJson(engineId, json)
    inputInternal(json: String, trainNow)
  }
  def query(json: String): Validated[ValidateError, String]
  def status(): String = "Does not support status message."

  protected def inputInternal(json: String, trainNow: Boolean = true): Validated[ValidateError, Boolean]
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
