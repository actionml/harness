package com.actionml

import akka.actor.ActorSystem
import cb.CBEngine
import com.actionml.core.admin.{Administrator, MongoAdministrator}
import com.actionml.core.template.Engine
import com.actionml.router.config.AppConfig
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes._
import com.actionml.router.service._
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

  bind[AppConfig] to AppConfig.apply

  bind[ActorSystem] to ActorSystem(inject[AppConfig].actorSystem.name) destroyWith(_.terminate())

  implicit lazy val system: ActorSystem = inject [ActorSystem]

  bind[RestServer] to new RestServer

  bind[CheckRouter] to new CheckRouter
  bind[EventsRouter] to new EventsRouter
  bind[EnginesRouter] to new EnginesRouter
  bind[QueriesRouter] to new QueriesRouter
  bind[CommandsRouter] to new CommandsRouter

  bind[EventService] to new EventServiceImpl
  bind[EngineService] to new EngineServiceImpl
  bind[QueryService] to new QueryServiceImpl

  binding identifiedBy 'EventService to AkkaInjectable.injectActorRef[EventService]("EventService")
  binding identifiedBy 'QueryService to AkkaInjectable.injectActorRef[QueryService]("QueryService")
  binding identifiedBy 'EngineService to AkkaInjectable.injectActorRef[EngineService]("EngineService")

  bind[Administrator] to new MongoAdministrator initWith(_.init())
  bind[Engine] to new CBEngine() // todo: Semen, this is supplied by the admin API now, this

}
