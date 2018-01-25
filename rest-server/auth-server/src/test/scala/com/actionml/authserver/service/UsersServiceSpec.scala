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

import com.actionml.authserver.Roles._
import com.actionml.authserver.config.{ActorSystemConfig, AppConfig, AuthServerConfig, MongoDbConfig}
import com.actionml.authserver.model.RoleSet
import org.scalatest._
import scaldi.NilInjector

class UsersServiceSpec extends FlatSpec with Matchers {

  "toRoleSetId" should "find roleSetId by roleId" in {
    usersService.toRoleSetIds(Set("engine_read", "query_create", "event_create")) should contain theSameElementsAs (List("client"))
  }

  it should "match roles with the same elements" in {
    usersService.toRoleSetIds(Set("engine_read", "query_create", "event_create", "user_permissions")) should contain theSameElementsAs (List("admin"))
  }

  def usersService = {
    implicit val _ = NilInjector
    new UsersServiceImpl {
      val roleSets = List(
        RoleSet("admin", List(user.permissions, engine.read, query.create, event.create)),
        RoleSet("client", List(engine.read, query.create, event.create))
      )
      override lazy val config = AppConfig(AuthServerConfig(roleSets = roleSets, host = "", sslEnabled = false, mongoDb = MongoDbConfig("", ""), authorizationEnabled = false, clients = List.empty), ActorSystemConfig(""))
    }
  }
}
