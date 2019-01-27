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
import com.actionml.router.ActorInjectable
import scaldi.Injector

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

    /*
    case UpdateEngineWithConfig(engineId, engineJson, dataDelete, force, input) =>
      log.info(s"Update existing engine, ${engineId}, ${engineJson}, ${dataDelete}, ${dataDelete}, ${force}, ${input}")
      sender() ! admin.updateEngine(engineId, Some(engineJson), dataDelete, force, Some(input)).map(_.asJson)

    case UpdateEngineWithId(engineId, dataDelete, force, input) =>
      log.info("Update existing engine, {}, {}, {}, {}", engineId, dataDelete, force, input)
      sender() ! admin.updateEngine(engineId, None, dataDelete, force, Some(input)).map(_.asJson)
    */

    case DeleteEngine(engineId) =>
      log.info("Delete existing engine, {}", engineId)
      sender() ! admin.removeEngine(engineId)
  }
}

sealed trait EngineAction
case class GetEngine(engineId: String) extends EngineAction
case object GetEngines extends EngineAction
case class CreateEngine(engineJson: String) extends EngineAction
case class UpdateEngine(engineJson: String) extends EngineAction
case class UpdateEngineWithTrain(engineId: String) extends EngineAction
case class UpdateEngineWithImport(engineId: String, inputPath: String) extends EngineAction

// keeping update simple, only allow sending new json config
//case class UpdateEngineWithConfig(engineId: String, engineJson: String, dataDelete: Boolean, force: Boolean, input: String) extends EngineAction
//case class UpdateEngineWithId(engineId: String, dataDelete: Boolean, force: Boolean, input: String) extends EngineAction
case class DeleteEngine(engineId: String) extends EngineAction
