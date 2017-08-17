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
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.actionml.authserver._
import com.actionml.router.config.AppConfig
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}


trait AuthServerClientService {
  def proxyAccessTokenRequest(request: HttpRequest)
                             (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[HttpResponse]

  def authorize(accessToken: AccessToken, role: RoleId, resourceId: ResourceId)
               (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[Boolean]
}

class SimpleAuthServerClientService(config: AppConfig) extends AuthServerClientService with FailFastCirceSupport {

  def proxyAccessTokenRequest(request: HttpRequest)
                             (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[HttpResponse] = {
    Http().singleRequest(mkAccessTokenRequest(request))
      .recoverWith {
        case ex =>
          log.error(ex, "Proxy authorization call failed")
          Future.failed(AuthenticationFailedException(ex))
      }
  }

  override def authorize(accessToken: AccessToken, role: RoleId, resourceId: ResourceId)
                        (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, log: LoggingAdapter): Future[Boolean] = {
    Http().singleRequest(mkAuthorizeRequest(accessToken, role, resourceId))
      .collect {
        case HttpResponse(StatusCodes.OK, _, _, _) => true
      }.recoverWith {
        case ex =>
          log.error(ex, "Access denied")
          Future.successful(false)
      }
  }


  private val authServerRoot = Uri(config.auth.authServerUrl)

  private def mkAccessTokenRequest(req: HttpRequest) =
    HttpRequest(method = req.method,
      uri = authServerRoot.withFragment("token"),
      entity = req.entity,
      headers = req.headers
    )

  private def mkAuthorizeRequest(accessToken: AccessToken, role: RoleId, resourceId: ResourceId) =
    HttpRequest(method = HttpMethods.POST,
      uri = authServerRoot
        .withFragment("authorize")
        .withQuery(Query("accessToken" -> accessToken, "roleId" -> role, "resourceId" -> resourceId)))
}
