package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.core.validate.NotImplemented
import com.actionml.router.ActorInjectable
import com.actionml.templates.cb.CBEngine
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 14:49
  */

trait EventService extends ActorInjectable

class CBEventService(implicit inj: Injector) extends EventService{

  private val engine = inject[CBEngine]

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
