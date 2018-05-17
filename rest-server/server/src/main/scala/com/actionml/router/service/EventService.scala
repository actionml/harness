package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
import com.actionml.core.engine.Engine
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

trait EventService extends ActorInjectable

class EventServiceImpl(implicit inj: Injector) extends EventService{

  private val admin = inject[Administrator]('Administrator)

  override def receive: Receive = {
    case GetEvent(engineId, eventId) ⇒
      log.debug("Get event, {}, {}", engineId, eventId)
      sender() ! Invalid(NotImplemented())

    case CreateEvent(engineId, event) ⇒
      log.debug("Receive new event & stored, {}, {}", engineId, event)
      admin.getEngine(engineId) match {
        case Some(engine) ⇒ sender() ! engine.input(event).map(_.asJson)
        case None ⇒ sender() ! Invalid(WrongParams(s"Engine for id=$engineId not found"))
      }

  }
}

sealed trait EventAction
case class GetEvent(engineId: String, eventId: String) extends EventAction
case class CreateEvent(engineId: String, event: String) extends EventAction
