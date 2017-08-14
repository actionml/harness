import akka.actor.ActorSystem
import akka.event.Logging
import com.actionml.authserver.AuthServer
import com.actionml.core.ExecutionContextComponent
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.routes.SecurityController
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:26
  */
object AuthServerBootstrap extends App with AkkaInjectable {

  implicit val injector = new BaseModule

  inject[AuthServer].run()
}

class BaseModule extends Module {

  bind[AppConfig] to AppConfig.apply

  implicit lazy val actorSystem = ActorSystem(inject[AppConfig].actorSystem.name)
  bind[ActorSystem] to actorSystem destroyWith(_.terminate())

  binding identifiedBy 'log to ((logSource: Class[_]) ⇒ Logging(inject[ActorSystem], logSource))

  trait ActorSystemExecutionContextComponent extends ExecutionContextComponent {
    override def executionContext: ExecutionContext = actorSystem.dispatcher
  }

  bind[SecurityController] to new SecurityController

  bind[AuthServer] to new AuthServer
}
