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

package com.actionml.security.directives

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.directives.{BasicDirectives, Credentials, RouteDirectives, SecurityDirectives}
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive0, Directive1}
import akka.stream.ActorMaterializer
import com.actionml.router.config.ConfigurationComponent
import com.actionml.security.Realms
import com.actionml.security.model.{ResourceId, Role, Secret}
import com.actionml.security.services.AuthServiceComponent

import scala.concurrent.ExecutionContext


trait AuthDirectives extends RouteDirectives with BasicDirectives {
  this: SecurityDirectives
    with ConfigurationComponent
    with AuthServiceComponent =>

  def extractOauth2Credentials: Directive1[Option[Secret]] = {
    if (config.auth.enabled) {
      authenticateOAuth2PF(Realms.Harness, {
        case Credentials.Provided(secret) => Some(secret)
      })
    } else provide(None)
  }

  def authorizeUser(role: Role, resourceId: ResourceId)
                   (implicit as: ActorSystem,
                    mat: ActorMaterializer,
                    ec: ExecutionContext,
                    secretOpt: Option[Secret],
                    log: LoggingAdapter): Directive0 = {
    if (config.auth.enabled) {
      secretOpt.fold[Directive0] {
        reject(AuthorizationFailedRejection)
      } { secret =>
        authorizeAsync(_ => authService.authorize(secret, role, resourceId))
      }
    } else pass
  }
}
