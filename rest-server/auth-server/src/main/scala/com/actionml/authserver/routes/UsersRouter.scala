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
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, ExceptionHandler, Route, ValidationRejection}
import akka.stream.ActorMaterializer
import com.actionml.authserver.ResourceId
import com.actionml.authserver.Roles.user
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.directives.AuthorizationDirectives
import com.actionml.authserver.exceptions.{InvalidRoleSetException, NotFoundException, UserNotFoundException}
import com.actionml.authserver.routes.UsersRouter.{CreateUserRequest, PermissionsRequest, PermissionsResponse}
import com.actionml.authserver.service.{AuthorizationService, UsersService}
import com.actionml.circe.CirceSupport
import io.circe.generic.auto._
import scaldi.{Injectable, Injector}

import scala.concurrent.ExecutionContext

class UsersRouter(implicit injector: Injector) extends Directives with Injectable with CirceSupport with AuthorizationDirectives {
  override val authorizationService = inject[AuthorizationService]
  private val config = inject[AppConfig]
  override val authEnabled = config.authServer.authorizationEnabled

  def route: Route = extractLog { implicit log =>
    (pathPrefix("auth" / "users") & handleExceptions(exceptionHandler(log)) & extractAccessToken) { implicit token =>
      pathEndOrSingleSlash {
        (get & parameters('offset.as[Int] ? 0, 'limit.as[Int] ? Int.MaxValue) & hasAccess(user.permissions)) { (offset, limit) =>
          onSuccess(usersService.list(offset = offset, limit = limit))(complete(_))
        } ~
        (post & entity(as[CreateUserRequest]) & hasAccess(user.create)) {
          case CreateUserRequest(roleSetId, resourceId) =>
            onSuccess(usersService.create(roleSetId, resourceId.getOrElse(ResourceId.*)))(complete(_))
        }
      } ~
      (pathPrefix(Segment) & hasAccess(user.permissions)) { userId =>
        pathEndOrSingleSlash {
          get { getUser(userId) } ~
          delete { deleteUser(userId) }
        } ~
        path("permissions") {
          (post & entity(as[PermissionsRequest])) { req =>
            val resourceId = req.resourceId.getOrElse(ResourceId.*)
            onSuccess(usersService.grantPermissions(userId, req.roleSetId, resourceId)) {
              complete(PermissionsResponse(userId, req.roleSetId, resourceId))
            }
          } ~
          (delete & parameters('roleSetId, 'resourceId ?)) { (roleSetId, resourceIdOpt) =>
            val resourceId = resourceIdOpt.getOrElse(ResourceId.*)
            onSuccess(usersService.revokePermissions(userId, roleSetId, resourceId)) {
              complete(PermissionsResponse(userId, roleSetId, resourceId))
            }
          }
        }
      }
    }
  }


  private def getUser(userId: String): Route = {
    onSuccess(usersService.find(userId).map(_.getOrElse(throw UserNotFoundException)))(complete(_))
  }

  private def deleteUser(userId: String): Route = {
    onSuccess(usersService.delete(userId))(complete(Map("userId" -> userId)))
  }

  private def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case e@InvalidRoleSetException =>
      log.error("Invalid role set id", e)
      reject(ValidationRejection("Invalid role", None))
    case e: NotFoundException =>
      log.error("Not found", e)
      complete(StatusCodes.NotFound)
    case e: Throwable =>
      log.error("Router error", e)
      complete(StatusCodes.InternalServerError)
  }

  private val usersService = inject[UsersService]
  private implicit val actorSystem = inject[ActorSystem]
  private implicit val materializer = inject[ActorMaterializer]
  private implicit val executionContext = inject[ExecutionContext]
}

object UsersRouter {
  case class CreateUserRequest(roleSetId: String, resourceId: Option[String])
  case class CreateUserResponse(userId: String, secret: String, roleSetId: String, resourceId: String)
  case class ListUserResponse(userId: String, roleSetId: String, engines: Iterable[String])

  case class PermissionsRequest(roleSetId: String, resourceId: Option[String])
  case class PermissionsResponse(userId: String, roleSetId: String, resourceId: String)
}
