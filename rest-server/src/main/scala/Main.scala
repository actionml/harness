import akka.actor.ActorSystem
import com.actionml.config.AppConfig
import com.actionml.http.RestServer
import com.actionml.http.routes.{DatasetsRouter, EnginesRouter, EventsRouter}
import com.actionml.service._
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

  bind[RestServer] to new RestServer

  bind[DatasetsRouter] to new DatasetsRouter
  bind[EventsRouter] to new EventsRouter
  bind[EnginesRouter] to new EnginesRouter

  bind[DatasetService] to new EmptyDatasetService
  bind[EventService] to new EmptyEventService
  bind[EngineService] to new EmptyEngineService

}
