package com.actionml.router.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.actionml.router.config.AppConfig
import com.actionml.router.http.directives.{CorsSupport, LoggingSupport}
import com.actionml.router.http.routes._
import akka.http.scaladsl.server.Directives._
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.Future

/**
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:56
  */
class RestServer(implicit inj: Injector) extends AkkaInjectable with CorsSupport with LoggingSupport{

  implicit private val actorSystem = inject[ActorSystem]
  implicit private val executor = actorSystem.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val config = inject[AppConfig].restServer

  private val check = inject[CheckRouter]
  private val datasets = inject[DatasetsRouter]
  private val events = inject[EventsRouter]
  private val engines = inject[EnginesRouter]
  private val queries = inject[QueriesRouter]

  private val route: Route = check.route ~ events.route ~ datasets.route ~ engines.route ~ queries.route

  def run(host: String = config.host, port: Int = config.port): Future[Http.ServerBinding] = {
//    if (config.ssl) {
//      Http().setDefaultServerHttpContext(https)
//    }
    Http().bindAndHandle(logResponseTime(route), host, port)
  }

//  private def https = {
//
//    val sslConfig = AkkaSSLConfig()
//
//    val password: Array[Char] = sslConfig.config.keyManagerConfig.keyStoreConfigs.head.password.get.toCharArray
//
//    val ks: KeyStore = KeyStore.getInstance("JKS")
//    val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("keys/localhost.jks")
//
//    require(keystore != null, "Keystore required!")
//    ks.load(keystore, password)
//
//    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
//    keyManagerFactory.init(ks, password)
//
//    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
//    tmf.init(ks)
//
//    val sslContext: SSLContext = SSLContext.getInstance("TLS")
//    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
//    ConnectionContext.https(sslContext)
//  }

}
