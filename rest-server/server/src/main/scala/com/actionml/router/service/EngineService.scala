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

package com.actionml.router.service

import com.actionml.admin.Administrator
import com.actionml.core.model.Response
import com.actionml.core.validate.ValidateError
import com.actionml.core.utils.ZIOUtil.ImplicitConversions.ValidatedImplicits._
import com.typesafe.scalalogging.LazyLogging
import zio.IO

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EngineService {
  def getSystemInfo: IO[ValidateError, Response]
  def status(engineId: String): IO[ValidateError, Response]
  def statuses(): IO[ValidateError, List[Response]]
  def addEngine(engineJson: String): IO[ValidateError, Response]
  def updateEngine(engineJson: String): IO[ValidateError, Response]
  def train(engineId: String): IO[ValidateError, Response]
  def importFromPath(engineId: String, importPath: String): IO[ValidateError, Response]
  def deleteEngine(engineId: String): IO[ValidateError, Response]
  def cancelJob(engineId: String, jobId: String): IO[ValidateError, Response]
}

class EngineServiceImpl(admin: Administrator) extends EngineService with LazyLogging {
  override def getSystemInfo: IO[ValidateError, Response] = admin.systemInfo()
  override def status(engineId: String): IO[ValidateError, Response] = admin.status(engineId)
  override def statuses(): IO[ValidateError, List[Response]] =  admin.statuses()
  override def addEngine(engineJson: String): IO[ValidateError, Response] = admin.addEngine(engineJson)
  override def updateEngine(engineJson: String): IO[ValidateError, Response] = admin.updateEngine(engineJson)
  override def train(engineId: String): IO[ValidateError, Response] = admin.updateEngineWithTrain(engineId)
  override def importFromPath(engineId: String, importPath: String): IO[ValidateError, Response] = admin.updateEngineWithImport(engineId, importPath)
  override def deleteEngine(engineId: String): IO[ValidateError, Response] = admin.removeEngine(engineId)
  override def cancelJob(engineId: String, jobId: String): IO[ValidateError, Response] = admin.cancelJob(engineId = engineId, jobId = jobId)
}
