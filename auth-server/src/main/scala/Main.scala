import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import com.actionml.config.AppConfig
import com.actionml.http.{HttpServer, OAuthRoutes}
import com.actionml.oauth2.OAuth2DataHandler
import com.actionml.oauth2.dal.memory.{AccountsMemoryDal, OAuthAccessTokensMemoryDal, OAuthAuthorizationCodesMemoryDal, OAuthClientsMemoryDal}
import com.actionml.oauth2.dal.{AccountsDal, OAuthAccessTokensDal, OAuthAuthorizationCodesDal, OAuthClientsDal}
import scaldi.Module
import scaldi.akka.AkkaInjectable

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

  bind[ActorSystem] to ActorSystem(inject[AppConfig].actorSystem.name) destroyWith(_.terminate())

  binding identifiedBy 'log to ((logSource: Class[_]) ⇒ Logging(inject[ActorSystem], logSource))

  bind[AccountsDal] to new AccountsMemoryDal
  bind[OAuthClientsDal] to new OAuthClientsMemoryDal
  bind[OAuthAccessTokensDal] to new OAuthAccessTokensMemoryDal
  bind[OAuthAuthorizationCodesDal] to new OAuthAuthorizationCodesMemoryDal

  bind[OAuth2DataHandler] to new OAuth2DataHandler
  bind[OAuthRoutes] to new OAuthRoutes
  bind[HttpServer] to new HttpServer

}
