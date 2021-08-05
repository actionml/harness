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

import cats.data.Validated
import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
import com.actionml.core.model.Response
import com.actionml.core.spark.SparkContextSupport.jsonComment
import com.actionml.core.validate.{NotImplemented, ValidRequestExecutionError, ValidateError, WrongParams}
import com.actionml.router.ActorInjectable
import scaldi.Injector

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.NonFatal

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EngineService extends ActorInjectable

class EngineServiceImpl(implicit inj: Injector) extends EngineService{

  private val admin = inject[Administrator]('Administrator)

  override def receive: Receive = {
    case GetSystemInfo() =>
      log.info("Get system info")
      sender() ! admin.systemInfo(ExecutionContext.Implicits.global)

    case GetEngine(engineId) =>
      log.info("Get engine, {}", engineId)
      sender() ! admin.status(engineId)

    case GetEngines =>
      log.info("Get one or all engine status")
      sender() ! admin.statuses()

    case CreateEngine(engineJson) =>
      log.info("Create new engine, {}", engineJson)
      sender() ! admin.addEngine(engineJson)

    case UpdateEngine(engineJson) =>
      log.info(s"Update existing engine, $engineJson")
      sender() ! admin.updateEngine(engineJson)

    case UpdateEngineWithTrain(engineId) =>
      log.info(s"Update existing engine, $engineId")
      sender() ! admin.updateEngineWithTrain(engineId)

    case UpdateEngineWithImport(engineId, inputPath) =>
      log.info(s"Update existing engine by importing, $inputPath")
      sender() ! admin.updateEngineWithImport(engineId, inputPath)

    case DeleteEngine(engineId) =>
      log.info("Delete existing engine, {}", engineId)
      sender() ! admin.removeEngine(engineId)

    case CancelJob(engineId, jobId) =>
      log.info(s"Cancel job $jobId for engine $engineId")
      sender() ! admin.cancelJob(engineId = engineId, jobId = jobId)

    case GetUserData(engineId, userId, num, from) =>
      admin.getEngine(engineId).fold {
        sender() ! Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $engineId")))
      } { engine =>
        sender() ! (try {
          engine.getUserData(userId, num, from)
        } catch {
          case _: NotImplementedError => Invalid(NotImplemented)
          case NonFatal(_) => Invalid(ValidRequestExecutionError)
        })
      }

    case DeleteUserData(engineId, userId) =>
      admin.getEngine(engineId).fold {
        sender() ! Invalid(WrongParams(jsonComment(s"Non-existent engine-id: $engineId")))
      } { engine =>
        sender() ! (try {
          engine.deleteUserData(userId)
        } catch {
          case _: NotImplementedError => Invalid(NotImplemented)
          case NonFatal(_) => Invalid(ValidRequestExecutionError)
        })
      }
  }
}

sealed trait EngineAction
case class GetSystemInfo() extends EngineAction
case class GetEngine(engineId: String) extends EngineAction
case object GetEngines extends EngineAction
case class CreateEngine(engineJson: String) extends EngineAction
case class UpdateEngine(engineJson: String) extends EngineAction
case class UpdateEngineWithTrain(engineId: String) extends EngineAction
case class UpdateEngineWithImport(engineId: String, inputPath: String) extends EngineAction
case class CancelJob(engineId: String, jobId: String) extends EngineAction
case class GetUserData(engineId: String, userId: String, num: Int, from: Int) extends EngineAction
case class DeleteUserData(engineId: String, userId: String) extends EngineAction

// keeping update simple, only allow sending new json config
//case class UpdateEngineWithConfig(engineId: String, engineJson: String, dataDelete: Boolean, force: Boolean, input: String) extends EngineAction
//case class UpdateEngineWithId(engineId: String, dataDelete: Boolean, force: Boolean, input: String) extends EngineAction
case class DeleteEngine(engineId: String) extends EngineAction
