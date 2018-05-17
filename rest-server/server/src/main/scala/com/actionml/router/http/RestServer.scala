package com.actionml.router.http

import java.io.{File, FileInputStream}
import java.security.{KeyStore, SecureRandom}

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.actionml.authserver.router.AuthServerProxyRouter
import com.actionml.router.config.AppConfig
import com.actionml.router.http.directives.{CorsSupport, LoggingSupport}
import com.actionml.router.http.routes._
import com.typesafe.config.ConfigFactory
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
class RestServer(implicit inj: Injector) extends AkkaInjectable with CorsSupport with LoggingSupport with LazyLogging {

  implicit val actorSystem = inject[ActorSystem]
  implicit val executor = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].restServer

  private val check = inject[CheckRouter]
  private val commands = inject[CommandsRouter]
  private val events = inject[EventsRouter]
  private val engines = inject[EnginesRouter]
  private val queries = inject[QueriesRouter]
  private val authProxy = inject[AuthServerProxyRouter]

  private val route: Route = (DebuggingDirectives.logRequest("Harness-Server", Logging.InfoLevel) & DebuggingDirectives.logRequestResult("Harness-Server", Logging.InfoLevel)) {
    authProxy.route ~ check.route ~ events.route ~ engines.route ~ queries.route ~ commands.route
  }

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
    if (config.sslEnabled) {
      Http().setDefaultServerHttpContext(https)
    }
    val serverType = if (config.sslEnabled) "https" else "http"
    logger.info(s"Start $serverType server $host:$port")
    val bindingFuture = Http().bindAndHandle(logResponseTime(route), host, port)
    bindingFuture.failed.foreach { e =>
      logger.error("Harness Server binding error", e)
      System.exit(1)
    }
    bindingFuture
  }

  private def https = {
    val sslConfig = AkkaSSLConfig(actorSystem).config
    val keyManagerConfig = sslConfig.keyManagerConfig.keyStoreConfigs.headOption.getOrElse(throw new RuntimeException("Key manager store should be configured"))
    val storeType = keyManagerConfig.storeType
    val storePath = keyManagerConfig.filePath.getOrElse(throw new RuntimeException("KeyStore file path is required"))
    val password = keyManagerConfig.password.getOrElse(throw new RuntimeException("KeyStore password is required")).toCharArray

    val keystore = KeyStore.getInstance(storeType)
    val keystoreFile = new FileInputStream(storePath)

    require(keystoreFile != null, "Keystore required!")
    keystore.load(keystoreFile, password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keystore, password)

    val tmf = TrustManagerFactory.getInstance("SunX509")
    tmf.init(keystore)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ConnectionContext.https(sslContext)
  }

}
