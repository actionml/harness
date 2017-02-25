package com.actionml.service

import com.actionml.ActorInjectable
import io.circe.Json
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 11:48
  */
trait QueryService extends ActorInjectable

class EmptyQueryService(implicit inj: Injector) extends QueryService{
  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.info("Get prediction, {}, {}", engineId, query)
      sender() ! None
  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: Json) extends QueryAction
