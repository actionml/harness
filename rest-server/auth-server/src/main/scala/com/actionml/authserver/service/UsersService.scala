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

package com.actionml.authserver.service

import java.security.SecureRandom
import java.util.UUID

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.mongo.MongoSupport
import com.actionml.authserver.dal.{RoleSetsDao, UsersDao}
import com.actionml.authserver.exceptions.{InvalidRoleSetException, UserNotFoundException}
import com.actionml.authserver.model.{Permission, RoleSet, UserAccount}
import com.actionml.authserver.routes.UsersRouter.CreateUserResponse
import com.actionml.authserver.util.PasswordUtils
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

trait UsersService {
  def create(roleSetId: String, resourceId: String): Future[CreateUserResponse]
  def grantPermissions(userId: String, roleSetId: String, resourceId: String): Future[_]
  def revokePermissions(userId: String, roleSetId: String): Future[_]
}

class UsersServiceImpl(implicit inj: Injector) extends UsersService with MongoSupport with Injectable with PasswordUtils {
  implicit private val _ = inject[ExecutionContext]
  private lazy val config = inject[AppConfig]
  private val usersDao = inject[UsersDao]
  private val roleSetsDao = inject[RoleSetsDao]

  override def create(roleSetId: String, resourceId: String): Future[CreateUserResponse] = {
    findRoleSet(roleSetId).fold[Future[CreateUserResponse]](Future.failed(InvalidRoleSetException)) { roleSet =>
      for {
        secret <- generateUserSecret
        secretHash = hash(secret)
        permissions = roleSet.roles.map(Permission(_, List(resourceId)))
        userId = UUID.randomUUID().toString
        user = UserAccount(userId, secretHash, permissions)
        _ <- usersDao.update(user)
      } yield CreateUserResponse(userId, secret, roleSetId, resourceId)
    }
  }

  override def grantPermissions(userId: String, roleSetId: String, resourceId: String): Future[_] = {
    for {
      user <- usersDao.find(userId).map(_.getOrElse(throw UserNotFoundException))
      roleSet = findRoleSet(roleSetId).getOrElse(throw InvalidRoleSetException)
      grantedPermissions = roleSet.roles.map(Permission(_, List(resourceId)))
      newPermissions = (user.permissions ++ grantedPermissions).distinct
      newUser = user.copy(permissions = newPermissions)
      _ <- usersDao.update(newUser)
    } yield ()
  }

  override def revokePermissions(userId: String, roleSetId: String): Future[_] = {
    for {
      user <- usersDao.find(userId).map(_.getOrElse(throw UserNotFoundException))
      roleSet = findRoleSet(roleSetId).getOrElse(throw InvalidRoleSetException)
      newPermissions = user.permissions.filterNot(r => roleSet.roles.contains(r.roleId))
      newUser = user.copy(permissions = newPermissions)
      _ <- usersDao.update(newUser)
    } yield ()
  }


  private def findRoleSet(id: String): Option[RoleSet] = {
    config.authServer.roleSets.find(_.id == id)
  }
  private def generateUserSecret = Future.fromTry {
    Try(new Random(new SecureRandom()).alphanumeric.take(64).mkString)
  }
}
