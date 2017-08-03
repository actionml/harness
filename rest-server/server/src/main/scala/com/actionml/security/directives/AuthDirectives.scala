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
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.{BasicDirectives, Credentials, RouteDirectives, SecurityDirectives}
import akka.stream.ActorMaterializer
import com.actionml.router.config.ConfigurationComponent
import com.actionml.security.Realms
import com.actionml.security.model.{ResourceId, Role}
import com.actionml.security.services.AuthServiceComponent

import scala.concurrent.ExecutionContext


trait AuthDirectives extends RouteDirectives with BasicDirectives {
  this: SecurityDirectives
    with ConfigurationComponent
    with AuthServiceComponent =>

  def authorize(role: Role, resourceId: ResourceId)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext): Directive0 = {
    if (config.auth.enabled) {
      authenticateOAuth2PFAsync(Realms.Harness, {
        case Credentials.Provided(secret) =>
          authService.authorize(secret, role, resourceId)
      }).map(_ => ())
    } else pass
  }
}
