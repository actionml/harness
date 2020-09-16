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
import com.actionml.authserver.service.AuthorizationService
import com.actionml.authserver.services.{AuthServerProxyService, AuthServerProxyServiceImpl, CachedAuthorizationService}
import com.actionml.core.config.AppConfig
import com.actionml.core.store.backends.MongoStorage
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes._
import com.actionml.router.service._
import com.typesafe.scalalogging.LazyLogging
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:54
  */
object HarnessServer extends App with AkkaInjectable with LazyLogging {

  implicit val injector = new BaseModule

  inject[RestServer].run()

  sys.addShutdownHook {
    logger.info("Shutting down Harness Server")
    MongoStorage.close
  }
}

class BaseModule extends Module with LazyLogging {

  val config = AppConfig.apply
  bind[AppConfig] to config

  implicit lazy val actorSystem = ActorSystem(inject[AppConfig].actorSystem.name)
  bind[ActorSystem] to actorSystem destroyWith(terminateActorSystem)

  bind[ExecutionContext] to actorSystem.dispatcher
  bind[ActorMaterializer] to ActorMaterializer()

  lazy val server = new RestServer
  bind[RestServer] to server

  bind[CheckRouter] to new CheckRouter
  bind[QueriesRouter] to new QueriesRouter
  bind[CommandsRouter] to new CommandsRouter
  bind[AuthServerProxyRouter] to new AuthServerProxyRouter(config)

  lazy val administrator = {
    val a = new Administrator{
      override def system: ActorSystem = actorSystem
    }
    a.init
    a
  }
  bind[Administrator] to administrator

  lazy val eventService = new EventServiceImpl(administrator)
  lazy val engineService =  new EngineServiceImpl(administrator)
  lazy val eventsRouter = new EventsRouter(eventService)
  bind[EventService] to eventService
  bind[EventsRouter] to eventsRouter
  bind[EngineService] to engineService

  bind[EnginesRouter] to new EnginesRouter(engineService)

  lazy val authService = new CachedAuthorizationService
  lazy val authServerProxy = new AuthServerProxyServiceImpl
  bind[AuthServerProxyService] to authServerProxy
  bind[AuthorizationService] to authService

  bind[QueryService] identifiedBy 'QueryService to new QueryServiceImpl(administrator, actorSystem)
  binding identifiedBy 'EngineService to AkkaInjectable.injectActorRef[EngineService]("EngineService")


  private def terminateActorSystem(system: ActorSystem): Unit = {
    logger.info("Terminating actor system in the Harness Server...")
    system.whenTerminated.onComplete { t =>
      logger.info(s"Actor system terminated: $t")
    }(scala.concurrent.ExecutionContext.Implicits.global)
    system.terminate
  }
}
