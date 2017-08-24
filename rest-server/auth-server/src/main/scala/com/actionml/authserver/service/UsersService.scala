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

import com.actionml.authserver.dal.mongo.MongoSupport
import com.actionml.authserver.dal.{RoleSetsDao, UsersDao}
import com.actionml.authserver.exceptions.InvalidRoleSetException
import com.actionml.authserver.model.{Permission, UserAccount}
import com.actionml.authserver.routes.UsersRouter.CreateUserResponse
import com.actionml.authserver.util.PasswordUtils
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait UsersService {
  def create(clientId: String, roleSetId: String, resourceId: String): Future[CreateUserResponse]
  def find(userId: String): Future[Option[UserAccount]]
}

class UsersServiceImpl(implicit inj: Injector) extends UsersService with MongoSupport with Injectable with PasswordUtils {
  implicit private val _ = inject[ExecutionContext]
  private val usersDao = inject[UsersDao]
  private val roleSetsDao = inject[RoleSetsDao]

  override def create(clientId: String, roleSetId: String, resourceId: String): Future[CreateUserResponse] = {
    for {
      roleSetOpt <- roleSetsDao.find(roleSetId)
      roleSet = roleSetOpt.getOrElse(throw InvalidRoleSetException)
      permissions = roleSet.roles.map(Permission(_, List(resourceId)))
      userId = UUID.randomUUID().toString
      secret = generateUserSecret
      secretHash = hash(secret)
      user = UserAccount(userId, secretHash, clientId, permissions)
      _ <- usersDao.update(user)
    } yield CreateUserResponse(userId, secret, roleSetId, resourceId)
  }


  private def generateUserSecret = new Random(new SecureRandom()).alphanumeric.take(64).mkString

  override def find(userId: String): Future[Option[UserAccount]] = ???
}
