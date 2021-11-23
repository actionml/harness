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

import zio._
import zio.logging.{Logging, log}


/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:54
  */
object HarnessServer extends App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      appConfig <- Managed.fromFunction { _: Any => AppConfig.apply() }
      actorSystem <- Managed.make(IO(ActorSystem(appConfig.actorSystem.name)).orDie)(system => Fiber.fromFuture(system.terminate()).join.ignore)
    } yield (appConfig, actorSystem)).use { case (appConfig, actorSystem) =>
      (for {
        _ <- IO.effect(assert(ElasticSearchClient.esNodes.nonEmpty))
        actorMaterializer = ActorMaterializer()(actorSystem)
        administrator = new Administrator {
          override def system: ActorSystem = actorSystem
          override def config: AppConfig = appConfig
        }.init()
        ec = actorSystem.dispatcher
        authService = new CachedAuthorizationService(appConfig.auth)(actorSystem, actorMaterializer, ec)
        authServerProxy = new AuthServerProxyServiceImpl(appConfig, actorSystem, actorMaterializer)
        queryService = new QueryServiceImpl(administrator, actorSystem)
        infoService = new InfoService(administrator)

        checkRouter = new CheckRouter()(actorSystem, ec, actorMaterializer, appConfig)
        queriesRouter = new QueriesRouter(authService, queryService)(actorSystem, ec, actorMaterializer, appConfig)
        authServerProxyRouter = new AuthServerProxyRouter(authServerProxy)(actorSystem, ec, actorMaterializer, appConfig)

        eventService = new EventServiceImpl(administrator)
        engineService = new EngineServiceImpl(administrator)
        eventsRouter = new EventsRouter(eventService, authService)(actorSystem, ec, actorMaterializer, appConfig)
        infoRouter = new InfoRouter(actorSystem, ec, actorMaterializer, appConfig, infoService)
        engineRouter = new EnginesRouter(engineService, authService)(actorSystem, ec, actorMaterializer, appConfig)

        restServer = new RestServer(
          actorSystem,
          actorMaterializer,
          appConfig.restServer,
          checkRouter,
          eventsRouter,
          engineRouter,
          queriesRouter,
          infoRouter,
          authServerProxyRouter
        )
        _ <- Fiber.fromFuture(restServer.run()).join.forever
        _ <- log.info("Shutting down Harness Server")
        _ <- IO.effect(MongoStorage.close())
      } yield ()).provideLayer(Logging.console() ++ ZEnv.live).exitCode
    }
  }
}
