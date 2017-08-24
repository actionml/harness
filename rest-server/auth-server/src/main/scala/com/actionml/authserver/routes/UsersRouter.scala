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

package com.actionml.authserver.routes

import akka.http.scaladsl.server.{Directives, Route}
import com.actionml.authserver.routes.UsersRouter.CreateUserRequest
import com.actionml.authserver.service.{AuthService, UsersService}
import com.actionml.circe.CirceSupport
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

class UsersRouter(implicit injector: Injector) extends Directives with Injectable with CirceSupport with ClientAuthentication {
  private val authService = inject[AuthService]
  private val usersService = inject[UsersService]

  def route: Route = (post & pathPrefix("auth") & authenticateClient(authService.authenticateClient)) { clientId =>
    (path("users") & entity(as[CreateUserRequest])) { request =>
      onSuccess(usersService.create(clientId, request.roleSetId, request.resourceId))(resp => complete(resp))
    }
  }
}

object UsersRouter {
  case class CreateUserRequest(roleSetId: String, resourceId: String)
  case class CreateUserResponse(userId: String, secret: String, roleSetId: String, resourceId: String)
}
