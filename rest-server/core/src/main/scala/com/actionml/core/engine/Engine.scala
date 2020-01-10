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
import com.actionml.core.backup.{FSMirror, HDFSMirror, Mirror}
import com.actionml.core.jobs.{JobManager, JobStatuses}
import com.actionml.core.model.{Comment, GenericEngineParams, Response}
import com.actionml.core.validate._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  */
abstract class Engine extends LazyLogging with JsonSupport {

  var engineId: String = _
  private var mirroring: Mirror = _
  val serverHome = sys.env("HARNESS_HOME")
  var modelContainer: String = _ // path to directory or place we can put a model file, not a file name.

  // Methods that must be overridden in extending Engines

  /** This is the Engine factory method called only when creating a new Engine, and named in the engine-config.json file
    * the contents of which are passed in as a string.
    * @param json the Engine's config JSON for initializing all objects
    * @return The extending Engine instance, initialized and ready for input and/or queries etc.
    */
  @deprecated("Companion factory objects should be used instead of this old factory method", "0.3.0")
  def initAndGet(json: String, update: Boolean): Engine

  /** This is to destroy a running Engine, such as when executing the CLI `harness delete engine-id` */
  def destroy(): Unit = {
    Await.result(Future.sequence(JobManager.getActiveJobDescriptions(engineId).map(d => JobManager.cancelJob(engineId, d.jobId))), 30.minutes)
  }

  /** Every query is processed by the Engine, which may result in a call to an Algorithm, must be overridden.
    *
    * @param json Format defined by the Engine
    * @return a string of JSON query result formated as defined by the Engine, may also be ValidateError if a bad query
    */
  def query(json: String): Future[Response]


  // This section defines methods that must be executed as inherited, but are optional for implementation in extending classes

  /** Initialize resources common to all engines
    *
    * @param params parsed for common params like mirroring location
    * @return may return a ValidateError if the parameters are n
    */
  private def createResources(params: GenericEngineParams): Validated[ValidateError, Response] = {
    engineId = params.engineId
    if (!params.mirrorContainer.isDefined || !params.mirrorType.isDefined) {
      logger.info(s"Engine-id: ${engineId}. No mirrorContainer defined for this engine so no event mirroring will be done.")
      mirroring = new FSMirror("", engineId) // must create because Mirror is also used for import Todo: decouple these for Lambda
      Valid(Comment("Mirror type and container not defined so falling back to localfs mirroring"))
    } else if (params.mirrorContainer.isDefined && params.mirrorType.isDefined) {
      val container = params.mirrorContainer.get
      val mType = params.mirrorType.get
      mType match {
        case "localfs" | "localFs" | "LOCALFS" | "local_fs" | "localFS" | "LOCAL_FS" =>
          mirroring = new FSMirror(container, engineId)
          Valid(Comment("Mirror to localfs"))
        case "hdfs" | "HDFS" =>
          mirroring = new HDFSMirror(container, engineId)
          Valid(Comment("Mirror to HDFS"))
        case mt => Invalid(WrongParams(jsonComment(s"mirror type $mt is not implemented")))
      }
    } else {
      Invalid(WrongParams(jsonComment(s"MirrorContainer is set but mirrorType is not, no mirroring will be done.")))
    }
  }

  /** This is called any time we are initializing a new Engine Object, after the factory has constructed it. The flag
    * update means to update an existing object, it is set to false when creating a new Engine. So this method handles
    * C(reate) and U(pdate) of CRUD */
  def init(json: String, update: Boolean = false): Validated[ValidateError, Response] = {
    parseAndValidate[GenericEngineParams](json).andThen { p =>
      if (!update) { // if not updating then must be creating
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
  def status(): Validated[ValidateError, Response] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(EngineStatus(engineId, "This Engine does not implement the status API"))
  }

  /** Every input is processed by the Engine first, which may pass on to and Algorithm and/or Dataset for further
    * processing. Must be inherited and extended.
    * @param json Input defined by each engine
    * @return Validated[ValidateError, ]status with error message
    */
  def input(json: String): Validated[ValidateError, Response] = {
    // flatten the event into one string per line as per Spark json collection spec
    mirroring.mirrorEvent(json.replace("\n", " ") + "\n")
      .andThen(_ => Valid(Comment("Input processed by base Engine")))
  }

  def inputMany: Seq[String] => Unit = _.foreach(input)

  def batchInput(inputPath: String): Validated[ValidateError, Response] = {
    val jobDescription = JobManager.addJob(engineId, comment = "batch import, non-Spark job", status = JobStatuses.executing)
    Future(mirroring.importEvents(this, inputPath))
      .onComplete(_ => JobManager.finishJob(jobDescription.jobId))
    Valid(jobDescription)
  }

  /** train is only used in Lambda offline learners */
  def train(): Validated[ValidateError, Response] = notImplemented(s"Train is not a valid operation for engineId: $engineId")

  def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response] = notImplemented(s"Cancel is not a valid operation for engineId: $engineId")


  private def notImplemented(message: String) = {
    logger.warn(message)
    Invalid(NotImplemented(jsonComment(message)))
  }
}

case class EngineStatus(engineId: String, comment: String) extends Response
