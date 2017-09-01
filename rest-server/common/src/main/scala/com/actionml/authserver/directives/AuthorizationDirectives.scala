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

package com.actionml.authserver.directives

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.directives.{BasicDirectives, Credentials, RouteDirectives, SecurityDirectives}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, Directive1}
import akka.stream.ActorMaterializer
import com.actionml.authserver.service.AuthorizationService
import com.actionml.authserver.{AccessToken, Realms, ResourceId, RoleId}

import scala.concurrent.ExecutionContext


trait AuthorizationDirectives extends RouteDirectives with BasicDirectives with SecurityDirectives {
  val authorizationService: AuthorizationService
  val authEnabled: Boolean

  def extractAccessToken: Directive1[Option[AccessToken]] = {
    if (authEnabled) {
      authenticateOAuth2PF(Realms.Harness, {
        case Credentials.Provided(secret) => Some(secret)
      })
    } else provide(None)
  }

  def hasAccess(role: RoleId, resourceId: ResourceId = ResourceId.*)
               (implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, accessTokenOpt: Option[AccessToken], log: LoggingAdapter): Directive0 = {
    if (authEnabled) {
      accessTokenOpt.fold[Directive0] {
        reject(AuthorizationFailedRejection)
      } { secret =>
        authorizeAsync(_ => authorizationService.authorize(secret, role, resourceId))
      }
    } else pass
  }
}
