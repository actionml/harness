import akka.actor.ActorSystem
import com.actionml.router.config.AppConfig
import com.actionml.router.http.RestServer
import com.actionml.router.http.routes.{DatasetsRouter, EnginesRouter, EventsRouter, QueriesRouter}
import com.actionml.router.service._
import com.actionml.templates.cb.{CBDataset, CBEngine, CBEngineParams}
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

class BaseModule extends Module {

  bind[AppConfig] to AppConfig.apply

  bind[ActorSystem] to ActorSystem(inject[AppConfig].actorSystem.name) destroyWith(_.terminate())

  bind[RestServer] to new RestServer

  bind[DatasetsRouter] to new DatasetsRouter
  bind[EventsRouter] to new EventsRouter
  bind[EnginesRouter] to new EnginesRouter
  bind[QueriesRouter] to new QueriesRouter

  bind[DatasetService] to new EmptyDatasetService
  bind[EventService] to new CBEventService
  bind[EngineService] to new EmptyEngineService
  bind[QueryService] to new CBQueryService

  bind[CBDataset] to new CBDataset() // Semen: no longer need the store, private to the dataset now

  bind[CBEngineParams] to new CBEngineParams
  bind[CBEngine] to new CBEngine(inject[CBDataset], inject[CBEngineParams])

}
