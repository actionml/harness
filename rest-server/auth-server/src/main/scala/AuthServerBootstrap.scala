import akka.actor.ActorSystem
import akka.event.Logging
import com.actionml.authserver.AuthServer
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.{AccessTokensDao, PermissionsDao}
import com.actionml.authserver.dal.mongo.{AccessTokensDaoImpl, PermissionsDaoImpl}
import com.actionml.authserver.routes.AuthorizationController
import com.actionml.authserver.service.{AuthService, AuthServiceImpl}
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
  bind[ExecutionContext] to actorSystem.dispatcher

  binding identifiedBy 'log to ((logSource: Class[_]) ⇒ Logging(inject[ActorSystem], logSource))

  bind[PermissionsDao] to new PermissionsDaoImpl
  bind[AccessTokensDao] to new AccessTokensDaoImpl
  bind[AuthService] to new AuthServiceImpl

  bind[AuthorizationController] to new AuthorizationController

  bind[AuthServer] to new AuthServer
}
