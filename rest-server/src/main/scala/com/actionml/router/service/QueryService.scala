package com.actionml.router.service

import com.actionml.core.template.Engine
import com.actionml.router.ActorInjectable
import io.circe.syntax._
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 11:48
  */
trait QueryService extends ActorInjectable

class QueryServiceImpl(implicit inj: Injector) extends QueryService{

  private val engine = inject[Engine]

  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      sender() ! engine.query(query).map(_.asJson)
  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: String) extends QueryAction
