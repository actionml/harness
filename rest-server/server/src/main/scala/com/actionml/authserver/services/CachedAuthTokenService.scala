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

package com.actionml.authserver.services

import com.actionml.authserver.service.AuthorizationService
import com.actionml.authserver.{AccessToken, ResourceId, RoleId}
import com.typesafe.scalalogging.LazyLogging
import scaldi.{Injectable, Injector}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class CachedAuthTokenService(implicit inj: Injector) extends ClientAuthorizationService with LazyLogging with Injectable {
  private implicit val _ = inject[ExecutionContext]
  import CachedAuthTokenService.tokenCache

  override def authorize(token: AccessToken, roleId: RoleId, resourceId: ResourceId): Future[Boolean] = {
    def fetchTokenAndUpdateCache = {
      super.authorize(token, roleId, resourceId).map { case result =>
        tokenCache.put((token, roleId, resourceId), result)
        result
      }
    }

    tokenCache.get((token, roleId, resourceId)).fold(fetchTokenAndUpdateCache)(Future.successful)
  }
}


object CachedAuthTokenService {
  private val tokenCache = new TrieMap[(AccessToken, RoleId, ResourceId), Boolean]()
}
