package com.actionml.http

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.actionml.config.AppConfig
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:18
  */
class HttpServer(implicit inj: Injector) extends AkkaInjectable{

  implicit private val actorSystem = inject[ActorSystem]
  implicit private val executor = actorSystem.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].restServer

  private val oAuthRoutes = inject[OAuthRoutes].routes

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
      Http().bindAndHandle(oAuthRoutes, host, port)
  }

}
