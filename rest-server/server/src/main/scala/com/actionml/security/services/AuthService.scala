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
import com.actionml.security.model.{Credentials, User}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}


trait AuthServiceComponent {
  def authService: AuthService
}
trait AuthService {
  def findUserByCredentials(credentials: Credentials): Future[User]
}

class SimpleAuthService(config: AppConfig)(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext)
  extends AuthService with FailFastCirceSupport {
  import io.circe.generic.auto._

  override def findUserByCredentials(credentials: Credentials): Future[User] = {
    Http().singleRequest(HttpRequest(uri = findUserByCredentialsUri(credentials)))
      .flatMap(Unmarshal(_).to[User])
  }

  private val authServerRoot = Uri(config.restServer.authServerRootUri)
  private def findUserByCredentialsUri(credentials: Credentials) =
    authServerRoot
      .withFragment("users")
      .withQuery(Query("credentials" -> credentials))
}

