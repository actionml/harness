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

package com.actionml.router.config

import java.net.URI

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

import scala.util.Properties


/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 19:09
  */
case class AppConfig(restServer: RestServerConfig, actorSystem: ActorSystemConfig, auth: AuthConfig)
case class RestServerConfig(
  host: String,
  port: Int,
  sslEnabled: Boolean
)
case class ActorSystemConfig(
  name: String
)

case class AuthConfig(enabled: Boolean,
  serverUrl: String,
  clientId: String,
  clientSecret: String)

object AppConfig {
  private val config = ConfigFactory.load()

  def apply: AppConfig = {
    val uri = new URI(Properties.envOrElse("HARNESS_URI", "ERROR: no HARNESS_URI set" ))
    new AppConfig(
      // restServer = config.as[RestServerConfig]("rest-server"), // this has bad config if not taken from env

      // Since these must be set in the env, it is a hard error if they are not found
      restServer = RestServerConfig(
        host = uri.getHost,
        port = uri.getPort,
        sslEnabled = uri.getScheme == "https"
      ),

      // These should be removed from conf and ALWAYS read from the env
      actorSystem = config.as[ActorSystemConfig]("actor-system"),
      auth = config.as[AuthConfig]("auth")
    )
  }
}

trait ConfigurationComponent {
  def config: AppConfig
}
