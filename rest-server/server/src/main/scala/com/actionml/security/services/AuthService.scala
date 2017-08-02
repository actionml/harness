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

package com.actionml.security.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.actionml.router.config.AppConfig
import com.actionml.security.model.{Credentials, ResourceId, Role, User}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}


trait AuthServiceComponent {
  def authService: AuthService
}
trait AuthService {
  def authenticate(credentials: Credentials): Future[User]
  def authorize(credentials: Credentials, role: Role, resourceId: ResourceId): Future[Unit]
}

class SimpleAuthService(config: AppConfig)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext)
  extends AuthService with FailFastCirceSupport {
  import io.circe.generic.auto._

  override def authenticate(credentials: Credentials): Future[User] = {
    Http().singleRequest(HttpRequest(uri = authenticateUri(credentials)))
      .flatMap(Unmarshal(_).to[User])
  }

  override def authorize(credentials: Credentials, role: Role, resourceId: ResourceId): Future[Unit] = {
    Http().singleRequest(HttpRequest(uri = authorizeUri(credentials, role, resourceId)))
      .map(_ => ???) // todo implement it with common oauth protocol class
  }


  private val authServerRoot = Uri(config.auth.uri)

  private def authenticateUri(credentials: Credentials) =
    authServerRoot
      .withFragment("authenticate")
      .withQuery(Query("credentials" -> credentials))

  private def authorizeUri(credentials: Credentials, role: Role, resourceId: ResourceId) =
    authServerRoot
      .withFragment("authorize")
      .withQuery(Query("credentials" -> credentials, "role" -> role, "resource" -> resourceId))
}

