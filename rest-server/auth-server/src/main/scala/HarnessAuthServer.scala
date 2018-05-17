import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import com.actionml.authserver.AuthServer
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.dal.mongo.{AccessTokensDaoImpl, ClientsDaoImpl, RoleSetsDaoImpl, UsersDaoImpl}
import com.actionml.authserver.dal.{AccessTokensDao, ClientsDao, RoleSetsDao, UsersDao}
import com.actionml.authserver.routes.{OAuth2Router, UsersRouter}
import com.actionml.authserver.service._
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:26
  */
object HarnessAuthServer extends App with AkkaInjectable {

  implicit val injector = new BaseModule

  inject[AuthServer].run()
}

class BaseModule extends Module {

  bind[AppConfig] to AppConfig.apply

  implicit lazy val actorSystem = ActorSystem(inject[AppConfig].actorSystem.name)
  bind[ActorSystem] to actorSystem destroyWith(_.terminate())
  bind[ExecutionContext] to actorSystem.dispatcher
  bind[ActorMaterializer] to ActorMaterializer()

  binding identifiedBy 'log to ((logSource: Class[_]) ⇒ Logging(inject[ActorSystem], logSource))

  bind[UsersDao] to new UsersDaoImpl
  bind[AccessTokensDao] to new AccessTokensDaoImpl
  bind[ClientsDao] to new ClientsDaoImpl
  bind[RoleSetsDao] to new RoleSetsDaoImpl

  bind[AuthService] to new AuthServiceImpl
  bind[AuthorizationService] to new AuthServiceImpl
  bind[UsersService] to new UsersServiceImpl

  bind[OAuth2Router] to new OAuth2Router
  bind[UsersRouter] to new UsersRouter

  bind[AuthServer] to new AuthServer
}
