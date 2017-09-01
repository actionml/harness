/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.authserver.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.parboiled2.ParserInput
import akka.stream.Materializer
import com.actionml.authserver._
import com.actionml.circe.CirceSupport
import com.actionml.router.config.AppConfig
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}


trait AuthServerProxyService {
  def proxyAuthRequest(request: HttpRequest): Future[HttpResponse]
}

class AuthServerProxyServiceImpl(implicit inj: Injector) extends AuthServerProxyService with CirceSupport with Injectable {
  private val config = inject[AppConfig]
  private implicit val ec = inject[ExecutionContext]
  private implicit val actorSystem = inject[ActorSystem]
  private implicit val materializer = inject[Materializer]

  override def proxyAuthRequest(request: HttpRequest): Future[HttpResponse] = {
    val proxyRequest = mkProxyRequest(request)
    Http().singleRequest(proxyRequest)
      .recoverWith {
        case ex =>
          Future.failed(AuthenticationFailedException(ex))
      }
  }


  private val authServerRoot = Uri(config.auth.serverUrl)

  private def mkProxyRequest(req: HttpRequest) =
    HttpRequest(method = req.method,
      uri = authServerRoot.copy(path = authServerRoot.path ++ req.uri.path, rawQueryString = req.uri.rawQueryString),
      entity = req.entity,
      headers = req.headers
    )
}
