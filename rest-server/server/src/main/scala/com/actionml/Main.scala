package com.actionml

import akka.actor.ActorSystem
import com.actionml.admin.{Administrator, MongoAdministrator}
import com.actionml.core.backup.{FSMirroring, HDFSMirroring, Mirroring}
import com.actionml.router.config.AppConfig
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes._
import com.actionml.router.service._
import com.typesafe.config.ConfigFactory
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

  // need to decide here what type of mirroring to use from server config
  // todo: for now, not using injection. mirroring is hard coded to localfs type and Engine is extended with a trait
  // todo: need to turn this on/off while running so no reboot is required.
  //if(mirrorType == Mirroring.localfs) bind[Mirroring] to FSMirroring else bind[Mirroring] to HDFSMirroring

  binding identifiedBy 'EventService to AkkaInjectable.injectActorRef[EventService]("EventService")
  binding identifiedBy 'QueryService to AkkaInjectable.injectActorRef[QueryService]("QueryService")
  binding identifiedBy 'EngineService to AkkaInjectable.injectActorRef[EngineService]("EngineService")

  bind[Administrator] to new MongoAdministrator initWith(_.init())
}
