package com.actionml.router.http.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.Json
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 16:15
  */
abstract class BaseRouter(implicit inj: Injector) extends AkkaInjectable with CirceSupport {

  implicit protected val actorSystem: ActorSystem = inject[ActorSystem]
  implicit protected val executor: ExecutionContext = actorSystem.dispatcher
  implicit val timeout = Timeout(5 seconds)
  val route: Route
  protected val putOrPost: Directive[Unit] = post | put

  def completeByCond(
    ifDefinedStatus: StatusCode,
    ifEmptyStatus: StatusCode
  )(ifDefinedResource: Future[Option[Json]]): Route =
    onSuccess(ifDefinedResource) {
      case Some(json) => complete(ifDefinedStatus, json)
      case None => complete(ifEmptyStatus, ifEmptyStatus.defaultMessage())
    }

  def completeByCond(
    ifDefinedStatus: StatusCode
  )(ifDefinedResource: Future[Either[Int, Json]]): Route =
    onSuccess(ifDefinedResource) {
      case Right(json) => complete(ifDefinedStatus, json)
      case Left(errcode) => complete(StatusCodes.BadRequest, "Code error: " + errcode)
    }

}
