package com.actionml.service

import com.actionml.ActorInjectable
import com.actionml.cb.CBEngine
import com.actionml.entity.PredictionQuery
import scaldi.Injector

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 25.02.17 11:48
  */
trait QueryService extends ActorInjectable

class CBQueryService(implicit inj: Injector) extends QueryService{

  private val engine = inject[CBEngine]

  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      val (cbQuery, errcode) = engine.parseAndValidateQuery(query)
      sender() ! Either.cond(errcode == 0, engine.query(cbQuery), errcode)
  }
}

class EmptyQueryService(implicit inj: Injector) extends QueryService{
  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      sender() ! None
  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: String) extends QueryAction
