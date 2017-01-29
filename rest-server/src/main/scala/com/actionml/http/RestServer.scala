package com.actionml.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.actionml.http.routes.{DatasetsRouter, EnginesRouter, EventsRouter}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.actionml.config.AppConfig
import com.actionml.http.directives.{CorsSupport, LoggingSupport}
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:56
  */
class RestServer(implicit inj: Injector) extends AkkaInjectable with CorsSupport with LoggingSupport{

  implicit private val actorSystem = inject[ActorSystem]
  implicit private val executor = actorSystem.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].restServer

  private val datasets = inject[DatasetsRouter]
  private val events = inject[EventsRouter]
  private val engines = inject[EnginesRouter]

  private val route: Route = events.route ~ datasets.route ~ engines.route

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
    Http().bindAndHandle(logResponseTime(route), host, port)
  }

}
