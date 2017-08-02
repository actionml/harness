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

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.{AuthenticationDirective, Credentials, SecurityDirectives}
import com.actionml.security.Realms
import com.actionml.security.model.{ResourceId, Role, User}
import com.actionml.security.services.AuthServiceComponent


trait AuthDirectives {
  this: SecurityDirectives
    with AuthServiceComponent =>

  def authenticateUser: AuthenticationDirective[User] = authenticateOAuth2PFAsync[User](Realms.Harness, {
    case Credentials.Provided(secret) =>
      authService.authenticate(secret)
    case Credentials.Missing =>
      throw new RuntimeException("rejected")
  })

  def authorizeUser(user: User, role: Role, resourceId: ResourceId): Directive0 = authorize {
    user.role == role &&
      (user.resourceId == ResourceId.* || user.resourceId == resourceId)
  }
}
