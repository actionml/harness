package com.actionml.router.service

import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
import com.actionml.core.engine.Engine
import com.actionml.core.validate.WrongParams
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

  private val admin = inject[Administrator]('Administrator)

  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      admin.getEngine(engineId) match {
        case Some(engine) ⇒ sender() ! engine.query(query).map(_.asJson)
        case None ⇒ sender() ! Invalid(WrongParams(s"Engine for id=$engineId not found"))
      }

  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: String) extends QueryAction
