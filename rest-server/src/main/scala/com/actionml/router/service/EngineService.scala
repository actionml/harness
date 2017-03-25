package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.core.validate.NotImplemented
import com.actionml.router.ActorInjectable
import com.actionml.templates.cb.CBEngine
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EngineService extends ActorInjectable

class CBEngineService(implicit inj: Injector) extends EngineService{

  private val engine = inject[CBEngine]

  override def receive: Receive = {
    case GetEngine(engineId) ⇒
      log.info("Get engine, {}", engineId)
      // TODO: Not Implemented in engine
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))

    case CreateEngine(engineJson) ⇒
      log.info("Create new engine, {}", engine)
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))

    case UpdateEngine(engineId, engineJson) ⇒
      log.info("Update exist engine, {}, {}", engineId, engine)
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))

    case DeleteEngine(engineId) ⇒
      log.info("Delete exist engine, {}", engineId)
      sender() ! Invalid(NotImplemented("Not Implemented in engine"))
  }
}

class EmptyEngineService(implicit inj: Injector) extends EngineService{
  override def receive: Receive = {
    case GetEngine(engineId) ⇒
      log.info("Get engine, {}", engineId)
      sender() ! None

    case CreateEngine(engine) ⇒
      log.info("Create new engine, {}", engine)
      sender() ! None

    case UpdateEngine(engineId, engine) ⇒
      log.info("Update exist engine, {}, {}", engineId, engine)
      sender() ! None

    case DeleteEngine(engineId) ⇒
      log.info("Delete exist engine, {}", engineId)
      sender() ! None
  }
}

sealed trait EngineAction
case class GetEngine(engineId: String) extends EngineAction
case class CreateEngine(engineJson: String) extends EngineAction
case class UpdateEngine(engineId: String, engineJson: String) extends EngineAction
case class DeleteEngine(engineId: String) extends EngineAction
