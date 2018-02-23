package com.actionml.router.service

import akka.pattern.pipe
import cats.data.Validated.Invalid
import com.actionml.admin.Administrator
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

  import context.dispatcher
  private val admin = inject[Administrator]

  override def receive: Receive = {
    case GetPrediction(engineId, query) ⇒
      log.debug("Get prediction, {}, {}", engineId, query)
      admin.getEngine(engineId) match {
        case Some(engine) ⇒ engine.query(query).map(_.map(_.asJson)) pipeTo sender()
        case None ⇒ sender() ! Invalid(WrongParams(s"Engine for id=$engineId not found"))
      }
  }
}

sealed trait QueryAction
case class GetPrediction(engineId: String, query: String) extends QueryAction
