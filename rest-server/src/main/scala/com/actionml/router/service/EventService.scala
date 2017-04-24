package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.core.template.Engine
import com.actionml.core.validate.NotImplemented
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

  private val engine = inject[Engine]

  override def receive: Receive = {
    case GetEvent(engineId, eventId) ⇒
      log.debug("Get event, {}, {}", engineId, eventId)
      sender() ! Invalid(NotImplemented())

    case CreateEvent(engineId, event) ⇒
      log.debug("Receive new event & stored, {}, {}", engineId, event)
      sender() ! engine.input(event).map(_.asJson)
  }
}

sealed trait EventAction
case class GetEvent(engineId: String, eventId: String) extends EventAction
case class CreateEvent(engineId: String, event: String) extends EventAction
