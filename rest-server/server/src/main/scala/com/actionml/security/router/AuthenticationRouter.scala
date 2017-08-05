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

package com.actionml.security.router

import akka.http.scaladsl.server.MalformedHeaderRejection
import akka.http.scaladsl.server.directives.Credentials
import com.actionml.router.config.AppConfig
import com.actionml.router.http.routes.BaseRouter
import com.actionml.security.Realms
import com.actionml.security.services.AuthService
import io.circe.generic.auto._
import io.circe.syntax._
import scaldi.Injector

class AuthenticationRouter(config: AppConfig)(implicit inj: Injector) extends BaseRouter {
  val authService = inject[AuthService]

  override val route = (path("authenticate") & post & extractLog) { implicit log =>
    if (config.auth.enabled) {
      authenticateOAuth2PFAsync(Realms.Harness, {
        case Credentials.Provided(token) =>
          authService.authenticate(token)
      }) {
        authResponse => complete(authResponse.asJson.noSpaces)
      }
    } else reject(MalformedHeaderRejection("Authorization", "Header not supported"))
  }

}
