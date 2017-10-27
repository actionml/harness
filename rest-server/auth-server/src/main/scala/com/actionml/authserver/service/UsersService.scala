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

import akka.event.LoggingAdapter
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.UsersDao
import com.actionml.authserver.dal.mongo.MongoSupport
import com.actionml.authserver.exceptions.{InvalidRoleSetException, UserNotFoundException}
import com.actionml.authserver.model.{Permission, RoleSet, UserAccount}
import com.actionml.authserver.routes.UsersRouter.{CreateUserResponse, ListUserResponse}
import com.actionml.authserver.util.PasswordUtils
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

trait UsersService {
  def create(roleSetId: String, resourceId: String): Future[CreateUserResponse]
  def list(offset: Int, limit: Int): Future[Iterable[ListUserResponse]]
  def find(userId: String): Future[Option[ListUserResponse]]
  def delete(userId: String): Future[Unit]
  def grantPermissions(userId: String, roleSetId: String, resourceId: String): Future[Unit]
  def revokePermissions(userId: String, roleSetId: String, resourceId: String): Future[Unit]
}

class UsersServiceImpl(implicit inj: Injector) extends UsersService with MongoSupport with Injectable
  with PasswordUtils with LazyLogging {

  implicit private lazy val _ = inject[ExecutionContext]
  protected lazy val config = inject[AppConfig]
  private lazy val usersDao = inject[UsersDao]

  override def create(roleSetId: String, resourceId: String): Future[CreateUserResponse] = {
    findRoleSet(roleSetId).fold[Future[CreateUserResponse]](Future.failed(InvalidRoleSetException)) { roleSet =>
      for {
        secret <- generateUserSecret
        secretHash = hash(secret)
        permissions = roleSet.roles.map(Permission(_, List(resourceId)))
        userId = UUID.randomUUID().toString
        user = UserAccount(userId, secretHash, permissions)
        _ <- usersDao.update(user)
        _ = logger.debug(s"Created user with id $userId and role set id $roleSetId")
      } yield CreateUserResponse(userId, secret, roleSetId, resourceId)
    }
  }

  override def list(offset: Int, limit: Int): Future[Iterable[ListUserResponse]] = {
    logger.debug(s"Trying to list $limit users with offset $offset")
    usersDao.list(offset = offset, limit = limit)
      .map { _.flatMap { toListUserResponse }}
  }

  def find(userId: String): Future[Option[ListUserResponse]] = {
    logger.debug(s"Trying to find user with id $userId")
    usersDao.find(userId)
      .map(_.flatMap(toListUserResponse(_).headOption))
  }

  def delete(userId: String): Future[Unit] = {
    logger.debug(s"Trying to delete user with id $userId")
    usersDao.delete(userId)
  }

  override def grantPermissions(userId: String, roleSetId: String, resourceId: String): Future[Unit] = {
    logger.debug(s"Trying to grant $roleSetId for engine $resourceId to user $userId")
    for {
      user <- usersDao.find(userId).map(_.getOrElse(throw UserNotFoundException))
      roleSet = findRoleSet(roleSetId).getOrElse(throw InvalidRoleSetException)
      grantedPermissions = roleSet.roles.map(Permission(_, List(resourceId)))
      newPermissions = (user.permissions ++ grantedPermissions).distinct
      newUser = user.copy(permissions = newPermissions)
      _ <- usersDao.update(newUser)
      _ = logger.debug(s"User $userId had been granted access to engine $resourceId as $roleSetId")
    } yield ()
  }

  override def revokePermissions(userId: String, roleSetId: String, resourceId: String): Future[Unit] = {
    logger.debug(s"Trying to revoke $roleSetId for engine $resourceId from user $userId")
    for {
      user <- usersDao.find(userId).map(_.getOrElse(throw UserNotFoundException))
      roleSet = findRoleSet(roleSetId).getOrElse(throw InvalidRoleSetException)
      newPermissions = user.permissions.filterNot(r => roleSet.roles.contains(r.roleId))
      newUser = user.copy(permissions = newPermissions)
      _ <- usersDao.update(newUser)
      _ = logger.debug(s"User $userId had been revoked access to engine $resourceId as $roleSetId")
    } yield ()
  }


  private[service] def toListUserResponse(u: UserAccount): Iterable[ListUserResponse] = {
    val resourcesIds = u.permissions.flatMap(_.resourcesIds).distinct
    toRoleSetIds(u.permissions.map(_.roleId).toSet).map { roleSetId =>
      ListUserResponse(u.id, roleSetId, resourcesIds)
    }
  }

  private def findRoleSet(id: String): Option[RoleSet] = {
    logger.debug(s"Trying to find role set with id $id")
    config.authServer.roleSets.find(_.id == id)
  }

  private[service] def toRoleSetIds(roleIds: Set[String]): List[String] = {
    config.authServer.roleSets.collect {
      case configRoleSet if roleIds == configRoleSet.roles.toSet => configRoleSet.id
    }
  }

  private def generateUserSecret = Future.fromTry {
    Try(new Random(new SecureRandom()).alphanumeric.take(64).mkString)
  }
}
