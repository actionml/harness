/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.router.http

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import com.actionml.authserver.router.AuthServerProxyRouter
import com.actionml.core.config.RestServerConfig
import com.actionml.router.http.directives.{CorsSupport, LoggingSupport}
import com.actionml.router.http.routes._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.sslconfig.akka.AkkaSSLConfig

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContext, Future}

/**
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:56
  */
class RestServer(
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer,
  config: RestServerConfig,
  check: CheckRouter,
  commands: CommandsRouter,
  events: EventsRouter,
  engines: EnginesRouter,
  queries: QueriesRouter,
  info: InfoRouter,
  authProxy: AuthServerProxyRouter) extends CorsSupport with LoggingSupport with LazyLogging {

  implicit private val _as: ActorSystem = actorSystem
  implicit private val _am: ActorMaterializer = actorMaterializer
  implicit private val executor: ExecutionContext = actorSystem.dispatcher

  private val route: Route =
    (DebuggingDirectives.logRequest("Root Route", Logging.DebugLevel) & DebuggingDirectives.logRequestResult("Root Route", Logging.DebugLevel)){
      check.route
    } ~
    (DebuggingDirectives.logRequest("Harness Server", Logging.InfoLevel) & DebuggingDirectives.logRequestResult("Harness Server", Logging.InfoLevel)) {
      authProxy.route ~ events.route ~ engines.route ~ queries.route ~ commands.route ~ info.route
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
