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

import backup.{FSMirroring, Mirroring}
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.model.GenericEngineParams
import com.actionml.core.validate.{JsonParser, ValidateError, WrongParams}
import com.typesafe.scalalogging.LazyLogging
import scaldi.Injector

import scala.concurrent.{ExecutionContext, Future}

/** Forms the Engine contract. Engines parse and validate input strings, probably JSON,
  * and sent the correct case class E extending Event of the extending
  * Engine. Queries work in a similar way. The Engine is a "Controller" in the MVC sense
  */
abstract class Engine(implicit val injector: Injector) extends LazyLogging with JsonParser {

  // Todo: not sure how to require a val dataset: Dataset, which takes a type of Event parameter Dataset[CBEvent]
  // for instance. Because each Dataset may have a different parameter type
  var engineId: String = _
  var engineFactory: String = _
  var engineParams: String = _
  var mirroring: Mirroring = _
  private var mirroringDiabled = true

  private def createSharedResources(params: GenericEngineParams): Validated[ValidateError, Boolean] = {
    if (params.mirrorContainer.isEmpty) {
      logger.info("No mirrorContainer defined for this engine so no event mirroring will be done.")
      createSharedDB(params)
    } else if (params.mirrorType.nonEmpty) {
      val container = params.mirrorContainer.get
      val mType = params.mirrorType.get
      mType match {
        case "localfs" =>
          mirroring = new FSMirroring(container)
          mirroringDiabled = false

          createSharedDB(params)
        case mt => Invalid(WrongParams(s"mirror type $mt is not implemented"))
      }
    } else {
      Invalid(WrongParams(s"MirrorContainer is set but mirrorType is not, no mirroring will be done."))
    }
  }

  private def createSharedDB(params: GenericEngineParams): Validated[ValidateError, Boolean] = {
    // injector.bind[UsersDao] to new UsersDaoImpl(params.sharedDBName.getOrElse(engineId))
    Valid(true)
  }

  def init(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    Future.successful(parseAndValidate[GenericEngineParams](json).andThen(createSharedResources))
  }

  def initAndGet(json: String)(implicit ec: ExecutionContext): Future[Engine]
  def destroy()(implicit ec: ExecutionContext): Future[Unit]
  def start(): Engine = { logger.trace(s"Starting base Engine with engineId:$engineId"); this }
  def stop(): Unit = { logger.trace(s"Stopping base Engine with engineId:$engineId") }
  def status(): Validated[ValidateError, String] = {
    logger.trace(s"Status of base Engine with engineId:$engineId")
    Valid(
      s"""
         |{
         |  "Engine class": "Status of base Engine with engineId:$engineId"
         |}
       """.stripMargin)
  }

  def input(json: String, trainNow: Boolean = true)(implicit ec: ExecutionContext): Future[Validated[ValidateError, Boolean]] = {
    if (!mirroringDiabled) mirroring.mirrorEvent(engineId, json.replace("\n", " ") + "\n")
    Future.successful(Valid(true))
  }

  def query(json: String)(implicit ec: ExecutionContext): Future[Validated[ValidateError, String]]
}
