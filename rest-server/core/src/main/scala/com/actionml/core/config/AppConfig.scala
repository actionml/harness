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

package com.actionml.core.config

import java.net.{InetAddress, URI}
import com.actionml.core.config.StoreBackend.StoreBackend
import com.actionml.core.{HIO, harnessRuntime}
import com.actionml.core.jobs.JobManagerConfig
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase
import zio.{Cause, RIO}
import zio.logging.log
import zio.system.env

import scala.concurrent.duration._
import scala.util.{Properties, Try}


/**
 *
 *
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * 29.01.17 19:09
 */
case class AppConfig(
  restServer: RestServerConfig,
  actorSystem: ActorSystemConfig,
  auth: AuthConfig,
  jobs: JobManagerConfig,
  etcdConfig: EtcdConfig,
  enginesBackend: StoreBackend
)

case class RestServerConfig(host: String, port: Int, sslEnabled: Boolean)
case class ActorSystemConfig(name: String, timeout: FiniteDuration)

case class AuthConfig(
  enabled: Boolean,
  serverUrl: String,
  clientId: String,
  clientSecret: String
)

case class EtcdConfig(endpoints: Seq[String], timeout: FiniteDuration)

object AppConfig {
  private lazy val config = ConfigFactory.load()

  def apply(): AppConfig = {
    val uri = new URI(Properties.envOrElse("HARNESS_URI", "ERROR: no HARNESS_URI set" ))
    val backend: StoreBackend = Try(StoreBackend.withName(sys.env("HARNESS_ENGINES_BACKEND")))
      .getOrElse(StoreBackend.mongo)
    val etcdConfig: EtcdConfig = {
      val defaultTimeout = 5.seconds
      val config = Properties
        .envOrNone("HARNESS_ETCD_ENDPOINTS")
        .map(s => EtcdConfig(s.split(",").map(_.trim).filter(_.nonEmpty), defaultTimeout))
        .getOrElse(EtcdConfig(Seq("http://localhost:2379"), defaultTimeout))
      if (backend == StoreBackend.etcd && config.endpoints.isEmpty)
      throw new RuntimeException("ERROR: no HARNESS_ETCD_ENDPOINTS set")
      config
    }
    AppConfig(
      // restServer = config.as[RestServerConfig]("rest-server"), // this has bad config if not taken from env

      // Since these must be set in the env, it is a hard error if they are not found
      restServer = RestServerConfig(
        host = uri.getHost,
        port = uri.getPort,
        sslEnabled = uri.getScheme == "https"
      ),

      // These should be removed from conf and ALWAYS read from the env
      actorSystem = config.as[ActorSystemConfig]("actor-system"),
      auth = config.as[AuthConfig]("auth"),
      jobs = config.as[JobManagerConfig]("jobs"),
      etcdConfig = etcdConfig,
      backend
    )
  }

  val hostName: HIO[String] = {
    env("HOSTNAME").map(_.getOrElse(InetAddress.getLocalHost.getHostName))
      .catchAll(e => log.error("Get $HOSTNAME error", Cause.fail(e)).as("unknown"))
  }

  @deprecated
  def hostname: String = harnessRuntime.unsafeRun(hostName)
}

object StoreBackend extends Enumeration {
  type StoreBackend = Value

  val mongo = Value("mongo")
  val etcd = Value("etcd")
}
