package com.actionml

import akka.actor.ActorSystem
import com.actionml.admin.{Administrator, MongoAdministrator}
import com.actionml.router.config.AppConfig
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes._
import com.actionml.router.service._
import com.actionml.security.router.AuthenticationRouter
import com.actionml.security.services.{AuthService, SimpleAuthService}
import scaldi.Module
import scaldi.akka.AkkaInjectable
/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:54
  */
object Main extends App with AkkaInjectable{

  implicit val injector = new BaseModule

  inject[RestServer].run()

}

class BaseModule extends Module{

  val config = AppConfig.apply
  bind[AppConfig] to config

  bind[ActorSystem] to ActorSystem(inject[AppConfig].actorSystem.name) destroyWith(_.terminate())

  implicit lazy val system: ActorSystem = inject [ActorSystem]

  bind[RestServer] to new RestServer

  bind[CheckRouter] to new CheckRouter
  bind[EventsRouter] to new EventsRouter
  bind[EnginesRouter] to new EnginesRouter
  bind[QueriesRouter] to new QueriesRouter
  bind[CommandsRouter] to new CommandsRouter
  bind[AuthenticationRouter] to new AuthenticationRouter(config)

  bind[EventService] to new EventServiceImpl
  bind[EngineService] to new EngineServiceImpl
  bind[QueryService] to new QueryServiceImpl
  bind[AuthService] to new SimpleAuthService(config)

  binding identifiedBy 'EventService to AkkaInjectable.injectActorRef[EventService]("EventService")
  binding identifiedBy 'QueryService to AkkaInjectable.injectActorRef[QueryService]("QueryService")
  binding identifiedBy 'EngineService to AkkaInjectable.injectActorRef[EngineService]("EngineService")

  bind[Administrator] to new MongoAdministrator initWith(_.init())

}
