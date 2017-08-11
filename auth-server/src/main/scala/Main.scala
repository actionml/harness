import akka.actor.ActorSystem
import akka.event.Logging
import com.actionml.core.ExecutionContextComponent
import com.actionml.oauth2.OAuth2DataHandler
import com.actionml.oauth2.dal.memory.{AccountsMemoryDal, OAuthAccessTokensMemoryDal, OAuthAuthorizationCodesMemoryDal, OAuthClientsMemoryDal}
import com.actionml.oauth2.dal.{AccountsDal, OAuthAccessTokensDal, OAuthAuthorizationCodesDal, OAuthClientsDal}
import com.actionml.router.config.AppConfig
import com.actionml.router.http.{HttpServer, OAuthRoutes}
import com.actionml.security.dal.mongo.{MongoAccessTokenDaoComponent, MongoPermissionDaoComponent}
import com.actionml.security.routes.SecurityController
import com.actionml.security.service.AuthServiceComponentImpl
import scaldi.Module
import scaldi.akka.AkkaInjectable

import scala.concurrent.ExecutionContext

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:26
  */
object Main extends App with AkkaInjectable{

  implicit val injector = new BaseModule

  inject[HttpServer].run()
}

class BaseModule extends Module {

  bind[AppConfig] to AppConfig.apply

  val actorSystem = ActorSystem(inject[AppConfig].actorSystem.name)
  bind[ActorSystem] to actorSystem destroyWith(_.terminate())

  binding identifiedBy 'log to ((logSource: Class[_]) ⇒ Logging(inject[ActorSystem], logSource))

  trait ActorSystemExecutionContextComponent extends ExecutionContextComponent {
    override def executionContext: ExecutionContext = actorSystem.dispatcher
  }

  val securityController = new SecurityController with AuthServiceComponentImpl
    with MongoAccessTokenDaoComponent with MongoPermissionDaoComponent
    with ActorSystemExecutionContextComponent

  bind[AccountsDal] to new AccountsMemoryDal
  bind[OAuthClientsDal] to new OAuthClientsMemoryDal
  bind[OAuthAccessTokensDal] to new OAuthAccessTokensMemoryDal
  bind[OAuthAuthorizationCodesDal] to new OAuthAuthorizationCodesMemoryDal

  bind[OAuth2DataHandler] to new OAuth2DataHandler
  bind[OAuthRoutes] to new OAuthRoutes
  bind[HttpServer] to new HttpServer(securityController)
}
