/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
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

package com.actionml.admin

import cats.data.Validated
import com.actionml.core.engine.Engine
import com.actionml.core.model.Response
import com.actionml.core.validate.ValidateError
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

/** Handles commands or Rest requests that are system-wide, not the concern of a single Engine */
abstract class Administrator extends LazyLogging {

  lazy val config: Config = ConfigFactory.load()

  // engine management
  def getEngine(engineId: String): Option[Engine]
  def addEngine(json: String): Validated[ValidateError, Response]
  def removeEngine(engineId: String): Validated[ValidateError, Response]
  def updateEngine(json: String): Validated[ValidateError, Response]
  def updateEngineWithTrain(engineId: String): Validated[ValidateError, Response]
  def updateEngineWithImport(engineId: String, inputPath: String): Validated[ValidateError, Response]
  def status(resourceId: String): Validated[ValidateError, Response]
  def statuses(): Validated[ValidateError, List[Response]]
  def systemInfo(): Validated[ValidateError, Response]
  def init(): Administrator = this
  def cancelJob(engineId: String, jobId: String): Validated[ValidateError, Response]
  def healthCheck(implicit ec: ExecutionContext): Future[Response]
}
