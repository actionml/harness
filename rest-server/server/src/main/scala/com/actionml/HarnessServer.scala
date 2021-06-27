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

package com.actionml

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.actionml.admin.Administrator
import com.actionml.authserver.router.AuthServerProxyRouter
import com.actionml.authserver.services.{AuthServerProxyServiceImpl, CachedAuthorizationService}
import com.actionml.core.config.AppConfig
import com.actionml.core.search.elasticsearch.ElasticSearchClient
import com.actionml.core.store.backends.MongoStorage
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes._
import com.actionml.router.service._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext


/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:54
  */
object HarnessServer extends App with LazyLogging {
  sys.addShutdownHook {
    logger.info("Shutting down Harness Server")
    MongoStorage.close
  }

  def start() = {
    implicit val config: AppConfig = AppConfig.apply
    assert(ElasticSearchClient.esNodes.nonEmpty)

    implicit val actorSystem: ActorSystem = ActorSystem(config.actorSystem.name)
    actorSystem.whenTerminated.onComplete { t =>
      logger.info(s"Actor system terminated: $t")
    }(scala.concurrent.ExecutionContext.Implicits.global)
    implicit val actorMaterializer = ActorMaterializer()(actorSystem)
    implicit val ec: ExecutionContext = actorSystem.dispatcher

    val administrator = {
      val a = new Administrator {
        override def system: ActorSystem = actorSystem
      }
      a.init
      a
    }

    val authService = new CachedAuthorizationService(config.auth)
    val authServerProxy = new AuthServerProxyServiceImpl(config, actorSystem, actorMaterializer)
    val queryService = new QueryServiceImpl(administrator, actorSystem)
    val infoService = new InfoService(administrator)

    val checkRouter = new CheckRouter
    val queriesRouter = new QueriesRouter(authService, queryService)
    val commandsRouter = new CommandsRouter
    val authServerProxyRouter = new AuthServerProxyRouter(authServerProxy)

    val eventService = new EventServiceImpl(administrator)
    val engineService = new EngineServiceImpl(administrator)
    val eventsRouter = new EventsRouter(eventService, authService)
    val engineRouter = new EnginesRouter(engineService, authService)
    val infoRouter = new InfoRouter(actorSystem, ec, actorMaterializer, config, infoService)

    new RestServer(
      actorSystem,
      actorMaterializer,
      config.restServer,
      checkRouter,
      commandsRouter,
      eventsRouter,
      engineRouter,
      queriesRouter,
      infoRouter,
      authServerProxyRouter
    ).run()
  }

  start()
}
