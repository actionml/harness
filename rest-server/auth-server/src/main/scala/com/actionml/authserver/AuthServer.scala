package com.actionml.authserver

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.routes.{OAuth2Router, UsersRouter}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import net.ceedubs.ficus.readers.ValueReader
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:18
  */
class AuthServer(implicit inj: Injector) extends AkkaInjectable with Directives {

  implicit private val actorSystem = inject[ActorSystem]
  implicit private val executor = actorSystem.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].authServer
  private val oauth2Router = inject[OAuth2Router]
  private val usersRouter = inject[UsersRouter]
  private val securityRouter = DebuggingDirectives.logRequestResult("Auth-Server", Logging.InfoLevel) {
    oauth2Router.route ~ usersRouter.route
  }

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
    if (config.sslEnabled) {
      Http().setDefaultServerHttpContext(https)
    }
    Http().bindAndHandle(securityRouter, host, port)
  }

  private def https = {
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

    val storeConfig = AppConfig.root.as[List[SslConfig]]("akka.ssl-config.keyManager.stores").headOption.getOrElse(throw MissingConfigException())
    val password = storeConfig.password.fold(throw MissingConfigException("password"))(_.toCharArray)
    val keyStore = KeyStore.getInstance(storeConfig.`type`)
    val ksInputStream = Files.newInputStream(Paths.get(storeConfig.path.getOrElse(throw MissingConfigException("path"))))
    require(ksInputStream != null, "Keystore required!")

    keyStore.load(ksInputStream, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(keyStore)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ConnectionContext.https(sslContext)
  }

  private object MissingConfigException {
    def apply(key: String): Exception = apply(Some(key))
    def apply(missingKey: Option[String] = None): Exception =
      new RuntimeException(s"HTTPS configuration is not valid - missing value at at [akka.ssl-config.keyManager.stores${missingKey.map(k => s".$k")}]. See https://lightbend.github.io/ssl-config/ for more details.")
  }
  private case class SslConfig(`type`: String, path: Option[String], password: Option[String])
}
