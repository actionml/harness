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
import com.actionml.core.backup.{FSMirroring, HDFSMirroring, Mirroring}
import com.actionml.core.model.GenericEngineParams
import com.actionml.core.validate._
import com.typesafe.scalalogging.LazyLogging

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  */
abstract class Engine extends LazyLogging with JsonParser {

  var engineId: String = _
  private var mirroring: Mirroring = _
  val serverHome = sys.env("HARNESS_HOME")
  var modelContainer: String = _ // path to directory or place we can put a model file, not a file name.

  // Methods that must be overridden in extending Engines

  /** This is the Engine factory method called only when creating a new Engine, and named in the engine-config.json file
    * the contents of which are passed in as a string.
    * @param json the Engine's config JSON for initializing all objects
    * @return The extending Engine instance, initialized and ready for input and/or queries etc.
    */
  def initAndGet(json: String): Engine

  /** This is to destroy a running Engine, such as when executing the CLI `harness delete engine-id` */
  def destroy(): Unit

  /** Every query is processed by the Engine, which may result in a call to an Algorithm, must be overridden.
    *
    * @param json Format defined by the Engine
    * @return a string of JSON query result formated as defined by the Engine, may also be ValidateError if a bad query
    */
  def query(json: String): Validated[ValidateError, String]


  // This section defines methods that must be executed as inherited, but are optional for implementation in extending classes

  /** Initialize resources common to all engines
    *
    * @param params parsed for common params like mirroring location
    * @return may return a ValidateError if the parameters are n
    */
  private def createResources(params: GenericEngineParams): Validated[ValidateError, Boolean] = {
    engineId = params.engineId
    if (!params.mirrorContainer.isDefined || !params.mirrorType.isDefined) {
      logger.info("No mirrorContainer defined for this engine so no event mirroring will be done.")
      mirroring = new FSMirroring("", engineId) // must create because Mirroring is also used for import Todo: decouple these for Lambda
      Valid(true)
    } else if (params.mirrorContainer.isDefined && params.mirrorType.isDefined) {
      val container = params.mirrorContainer.get
      val mType = params.mirrorType.get
      mType match {
        case "localfs" | "localFs" | "LOCALFS" | "local_fs" | "localFS" | "LOCAL_FS" =>
          mirroring = new FSMirroring(container, engineId)
          Valid(true)
        case "hdfs" =>
          mirroring = new HDFSMirroring(container, engineId)
          Valid(true)
        case mt => Invalid(WrongParams(s"mirror type $mt is not implemented"))
      }
    } else {
      Invalid(WrongParams(s"MirrorContainer is set but mirrorType is not, no mirroring will be done."))
    }
  }

  /** This is called any time we are initializing a new Engine Object, after the factory has constructed it. The flag
    * deepInit means to initialize a new object, it is set to false when updating a running Engine.*/
  def init(json: String, deepInit: Boolean = true): Validated[ValidateError, Boolean] = {

    parseAndValidate[GenericEngineParams](json).andThen { p =>
      if (deepInit) {
        val container = if (serverHome.tail == "/") serverHome else serverHome + "/"
        modelContainer = p.modelContainer.getOrElse(container)
      } // not allowed to change with `harness update`
      createResources(p)
    }
  }

  // todo: do we need these? seems like destroy is the only use and it doesn't need an abstract class method
  /** Optional, used to stop Actors when destroying an Engine or other shutdown code */
  // def start(): Engine = { logger.trace(s"Starting base Engine with engineId:$engineId"); this }
  // def stop(): Unit = { logger.trace(s"Stopping base Engine with engineId:$engineId") }

  /** This returns information about a running Engine, any useful stats can be displayed in the CLI with
    * `harness status engine-id`. Typically overridden in child and not inherited.
    * todo: can we combine the json output so this can be inherited to supply status for the data the Engine class
    * manages and the extending Engine adds json to give stats about the data it manages?
    */
  def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(
      s"""
         |{
         |  "Engine id: ": "$engineId",
         |  "Comment: ": "This Engine does not implement the status API"
         |}
       """.stripMargin)
  }

  /** Every input is processed by the Engine first, which may pass on to and Algorithm and/or Dataset for further
    * processing. Must be inherited and extended.
    * @param json Input defined by each engine
    * @return Validated status with error message
    */
  def input(json: String): Validated[ValidateError, Boolean] = {
    // flatten the event into one string per line as per Spark json collection spec
    mirroring.mirrorEvent(json.replace("\n", " ") + "\n")
    Valid( true )
  }

  def batchInput(inputPath: String): Validated[ValidateError, Boolean] = {
    mirroring.importEvents(this, inputPath)
    Valid( true )
  }

  /** train is only used in Lambda offline learners */
  def train(): Validated[ValidateError, String] = {
    logger.warn(s"Train is not a valid operation for engineId: ${engineId}")
    Invalid(NotImplemented(s"Train is not a valid operation for engineId: ${engineId}"))
  }

}
