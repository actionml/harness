package com.actionml.http.directives

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import com.typesafe.config.ConfigFactory

/**
  * Cross-origin resource sharing
  * <a href="https://en.wikipedia.org/wiki/Cross-origin_resource_sharing">https://en.wikipedia.org/wiki/Cross-origin_resource_sharing</a>
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:58
  */
trait CorsSupport {
  lazy val allowedOriginHeader: `Access-Control-Allow-Origin` = {
    val config = ConfigFactory.load()
    val sAllowedOrigin = config.getString("cors.allowed-origin")
    if (sAllowedOrigin == "*")
      `Access-Control-Allow-Origin`.*
    else
      `Access-Control-Allow-Origin`(HttpOrigin(sAllowedOrigin))
  }

  private def addAccessControlHeaders: Directive0 = {
    mapResponseHeaders { headers =>
      allowedOriginHeader +:
        `Access-Control-Allow-Credentials`(true) +:
        `Access-Control-Allow-Headers`("Token", "Content-Type", "X-Requested-With") +:
        headers
    }
  }

  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(200).withHeaders(
      `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)
    )
    )
  }

  def corsHandler(r: Route): Route = addAccessControlHeaders {
    preflightRequestHandler ~ r
  }
}
