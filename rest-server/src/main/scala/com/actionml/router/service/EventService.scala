package com.actionml.router.service

import com.actionml.ActorInjectable
import com.actionml.templates.cb.CBEngine
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
    case GetEvent(datasetId, eventId) ⇒
      log.debug("Get event, {}, {}", datasetId, eventId)
      sender() ! None

    case CreateEvent(datasetId, event) ⇒
      log.debug("Receive new event & stored, {}, {}", datasetId, event)
      val (cbEvent, errcode) = engine.parseAndValidateInput(event)
      sender() ! Either.cond(errcode == 0, engine.input(cbEvent), errcode)
  }
}

class EmptyEventService(implicit inj: Injector) extends EventService{
  override def receive: Receive = {
    case GetEvent(datasetId, eventId) ⇒
      log.info("Get event, {}, {}", datasetId, eventId)
      sender() ! None

    case CreateEvent(datasetId, event) ⇒
      log.info("Receive new event & stored, {}, {}", datasetId, event)
      sender() ! None
  }
}

sealed trait EventAction
case class GetEvent(datasetId: String, eventId: String) extends EventAction
case class CreateEvent(datasetId: String, event: String) extends EventAction
