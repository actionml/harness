package com.actionml.router.http

import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import com.actionml.authserver.router.AuthServerProxyRouter
import com.actionml.router.config.AppConfig
import com.actionml.router.http.directives.{CorsSupport, LoggingSupport}
import com.actionml.router.http.routes._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:56
  */
class RestServer(implicit inj: Injector) extends AkkaInjectable with CorsSupport with LoggingSupport with LazyLogging{

  implicit val actorSystem = inject[ActorSystem]
  implicit val executor = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].restServer

  private val check = inject[CheckRouter]
  private val commands = inject[CommandsRouter]
  private val events = inject[EventsRouter]
  private val engines = inject[EnginesRouter]
  private val queries = inject[QueriesRouter]
  private val auth = inject[AuthServerProxyRouter]

  private val route: Route = DebuggingDirectives.logRequestResult("Harness-Server", Logging.DebugLevel) {
    auth.route ~ check.route ~ events.route ~ engines.route ~ queries.route ~ commands.route
  }

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
    if (config.ssl) {
      Http().setDefaultServerHttpContext(https)
    }
    logger.info(s"Start http server $host:$port")
    Http().bindAndHandle(logResponseTime(route), host, port)
  }

  private def https = {

    val sslConfig = AkkaSSLConfig()

    val password: Array[Char] = sslConfig.config.keyManagerConfig.keyStoreConfigs.head.password.get.toCharArray

    val ks = KeyStore.getInstance("JKS")
    val keystore = getClass.getClassLoader.getResourceAsStream("keys/localhost.jks")

    require(keystore != null, "Keystore required!")
    ks.load(keystore, password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ConnectionContext.https(sslContext)
  }

}
