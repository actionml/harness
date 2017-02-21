package com.actionml.http

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.actionml.config.AppConfig
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 18.02.17 19:18
  */
class HttpServer(implicit inj: Injector) extends AkkaInjectable {

  implicit private val actorSystem = inject[ActorSystem]
  implicit private val executor = actorSystem.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  val password: Array[Char] = "rRbYCMAAd7kcvqHbFpARy4JMrzsA7AdTMNmoFsqvUpgCTbJiU4jbrAqV3y3ojNL7xFTyMYhnWtAMN4jqYkahbMjhKAutPnWNHPKEyPyfzioJXrEoWAnyh9jfwRfNrFRm".toCharArray // do not store passwords in code, read them from somewhere safe!
  val ks: KeyStore = KeyStore.getInstance("JKS")
  val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("keys/localhost.jks")
  val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")

  require(keystore != null, "Keystore required!")
  ks.load(keystore, password)
  val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, password)
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  tmf.init(ks)
  val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
  sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
  private val config = inject[AppConfig].restServer
  private val oAuthRoutes = inject[OAuthRoutes].routes

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
    Http().setDefaultServerHttpContext(https)
    Http().bindAndHandle(oAuthRoutes, host, port)
  }

}
