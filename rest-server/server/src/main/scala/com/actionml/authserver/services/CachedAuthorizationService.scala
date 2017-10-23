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

import java.util.concurrent.TimeUnit

import com.actionml.authserver.{AccessToken, ResourceId, RoleId}
import com.typesafe.scalalogging.LazyLogging
import org.ehcache.{CacheManager, ValueSupplier}
import org.ehcache.config.builders.{CacheConfigurationBuilder, CacheManagerBuilder, ResourcePoolsBuilder}
import org.ehcache.expiry.{Duration, Expirations, Expiry}
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class CachedAuthorizationService(implicit inj: Injector) extends ClientAuthorizationService with LazyLogging with Injectable {
  private implicit val _ = inject[ExecutionContext]
  import CachedAuthorizationService.tokenCache

  override def authorize(token: AccessToken, roleId: RoleId, resourceId: ResourceId): Future[Boolean] = {
    val key = (token, roleId, resourceId)
    if (tokenCache.containsKey(key)) {
      Future.successful(tokenCache.get(key))
    } else {
      super.authorize(token, roleId, resourceId).map { result =>
        tokenCache.put(key, result)
        result
      }
    }
  }
}


object CachedAuthorizationService {
  val cacheTtl = Duration.of(30, TimeUnit.MINUTES)
  val cacheSize = 1000
  private class CacheExpiry[K,V] extends Expiry[K,V] {
    def getExpiryForUpdate(key: K, oldValue: ValueSupplier[_ <: V], newValue: V): Duration = cacheTtl
    def getExpiryForCreation(key: K, value: V): Duration = cacheTtl
    def getExpiryForAccess(key: K, value: ValueSupplier[_ <: V]): Duration = cacheTtl
  }
  private val cacheManager: CacheManager = CacheManagerBuilder.newCacheManagerBuilder
    .withCache("access_tokens", CacheConfigurationBuilder.newCacheConfigurationBuilder(
      classOf[(AccessToken, RoleId, ResourceId)],
      classOf[java.lang.Boolean],
      ResourcePoolsBuilder.heap(cacheSize)
    ).withExpiry(new CacheExpiry)).build
  cacheManager.init()
  private lazy val tokenCache = cacheManager.getCache("access_tokens", classOf[(AccessToken, RoleId, ResourceId)], classOf[java.lang.Boolean])
}
