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

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route, ValidationRejection}
import akka.stream.ActorMaterializer
import com.actionml.authserver.ResourceId
import com.actionml.authserver.Roles.user
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.exceptions.InvalidRoleSetException
import com.actionml.authserver.routes.UsersRouter.{CreateUserRequest, PermissionsRequest, PermissionsResponse}
import com.actionml.authserver.service.{AuthorizationService, UsersService}
import com.actionml.circe.CirceSupport
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext

class UsersRouter(implicit injector: Injector) extends Directives with Injectable with CirceSupport with AuthorizationDirectives {
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  override val authEnabled = !config.authServer.authorizationDisabled

  def route: Route = (handleExceptions(exceptionHandler) & extractLog) { implicit log =>
    (pathPrefix("auth" / "users") & extractAccessToken) { implicit token =>
      (pathEndOrSingleSlash & post & hasAccess(user.create) & entity(as[CreateUserRequest])) {
        case CreateUserRequest(roleSetId, resourceId) =>
          onSuccess(usersService.create(roleSetId, resourceId))(complete(_))
      } ~
      (path(Segment / "permissions") & hasAccess(user.permissions)) { userId =>
        (post & entity(as[PermissionsRequest])) { case PermissionsRequest(roleSetId, resourceId) =>
          onSuccess(usersService.grantPermissions(userId, roleSetId, resourceId)) { _ =>
            complete(PermissionsResponse(userId, roleSetId, resourceId))
          }
        } ~
        (delete & parameter('roleSetId)) { roleSetId =>
          onSuccess(usersService.revokePermissions(userId, roleSetId)) { _ =>
            complete(PermissionsResponse(userId, roleSetId, ResourceId.*))
          }
        }
      }
    }
  }


  private def exceptionHandler = ExceptionHandler {
    case ex@InvalidRoleSetException => reject(ValidationRejection("", Some(ex)))
  }
  private val usersService = inject[UsersService]
  private implicit val actorSystem = inject[ActorSystem]
  private implicit val materializer = inject[ActorMaterializer]
  private implicit val executionContext = inject[ExecutionContext]
}

object UsersRouter {
  case class CreateUserRequest(roleSetId: String, resourceId: String)
  case class CreateUserResponse(userId: String, secret: String, roleSetId: String, resourceId: String)

  case class PermissionsRequest(roleSetId: String, resourceId: String)
  case class PermissionsResponse(userId: String, roleSetId: String, resourceId: String)
}
