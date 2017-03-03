package com.actionml.router.service

import com.actionml.router.ActorInjectable
import com.actionml.entity.Engine
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EngineService extends ActorInjectable

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
case class CreateEngine(engine: Engine) extends EngineAction
case class UpdateEngine(engineId: String, engine: Engine) extends EngineAction
case class DeleteEngine(engineId: String) extends EngineAction
