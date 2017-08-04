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

package com.actionml.security.services

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.actionml.router.config.AppConfig
import com.actionml.security.AuthenticationFailedException
import com.actionml.security.model._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}


trait AuthServiceComponent {
  def authService: AuthService
}
trait AuthService {
  def authenticate(bearerToken: BearerToken)
                  (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[AuthenticationResponse]

  def authorize(accessToken: AccessToken, role: Role, resourceId: ResourceId)
               (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[Boolean]
}

class SimpleAuthService(config: AppConfig) extends AuthService with FailFastCirceSupport {

  def authenticate(bearerToken: BearerToken)
                  (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[AuthenticationResponse] = {
    import io.circe.generic.auto._
    Http().singleRequest(HttpRequest(uri = authenticateUri(bearerToken)))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, body, _) =>
          Unmarshal(body).to[AuthenticationResponse]
      }.recoverWith {
        case ex =>
          log.error(ex, "Authentication failed")
          Future.failed(AuthenticationFailedException(ex))
        }
  }

  override def authorize(accessToken: AccessToken, role: Role, resourceId: ResourceId)
                        (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[Boolean] = {
    Http().singleRequest(HttpRequest(uri = authorizeUri(accessToken, role, resourceId)))
      .collect {
        case HttpResponse(StatusCodes.OK, _, _, _) => true
      }.recoverWith {
        case ex =>
          log.error(ex, "Access denied")
          Future.successful(false)
      }
  }


  private val authServerRoot = Uri(config.auth.authServerUrl)

  private def authenticateUri(bearerToken: BearerToken) =
    authServerRoot
      .withFragment("authenticate")
      .withQuery(Query("bearerToken" -> bearerToken))

  private def authorizeUri(accessToken: AccessToken, role: Role, resourceId: ResourceId) =
    authServerRoot
      .withFragment("authorize")
      .withQuery(Query("accessToken" -> accessToken, "role" -> role, "resource" -> resourceId))
}
