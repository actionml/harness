package com.actionml.router.service

import cats.data.Validated.{Invalid, Valid}
import com.actionml.admin.Administrator
import com.actionml.core.template.Engine
import com.actionml.core.validate.{NotImplemented, WrongParams}
import com.actionml.router.ActorInjectable
import io.circe.syntax._
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EngineService extends ActorInjectable

class EngineServiceImpl(implicit inj: Injector) extends EngineService{

  private val admin = inject[Administrator]

  override def receive: Receive = {
    case GetEngine(engineId) ⇒
      log.info("Get engine, {}", engineId)
      // TODO: Not Implemented in engine
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))

    case GetEngines(resourceId) ⇒
      log.info("Get all engines")
      sender() ! admin.list(resourceId).map(_.asJson)

    case CreateEngine(engineJson) ⇒
      log.info("Create new engine, {}", engineJson)
      sender() ! admin.addEngine(engineJson).map(_.asJson)

    case UpdateEngineWithConfig(engineId, engineJson, dataDelete, force) ⇒
      log.info("Update exist engine, {}, {}, {}, {}", engineId, engineJson, dataDelete, force)
      admin.removeEngine(engineId)
      sender() ! admin.addEngine(engineJson).map(_.asJson)

    case UpdateEngineWithId(engineId, dataDelete, force) ⇒
      log.info("Update exist engine, {}, {}, {}", engineId, dataDelete, force)
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))

    case DeleteEngine(engineId) ⇒
      log.info("Delete exist engine, {}", engineId)
      sender() ! admin.removeEngine(engineId).map(_.asJson)
  }
}

sealed trait EngineAction
case class GetEngine(engineId: String) extends EngineAction
case class GetEngines(resourceId: String) extends EngineAction
case class CreateEngine(engineJson: String) extends EngineAction
case class UpdateEngineWithConfig(engineId: String, engineJson: String, dataDelete: Boolean, force: Boolean) extends EngineAction
case class UpdateEngineWithId(engineId: String, dataDelete: Boolean, force: Boolean) extends EngineAction
case class DeleteEngine(engineId: String) extends EngineAction
