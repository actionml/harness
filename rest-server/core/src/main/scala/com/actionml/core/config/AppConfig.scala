package com.actionml.core.config

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 19:09
  */
case class AppConfig(restServer: RestServerConfig, actorSystem: ActorSystemConfig, auth: AuthConfig, mongoServer: MongoDbConfig)

object AppConfig {
  private val config = ConfigFactory.load()

  def apply: AppConfig = new AppConfig(
    restServer = config.as[RestServerConfig]("rest-server"),
    actorSystem = config.as[ActorSystemConfig]("actor-system"),
    auth = config.as[AuthConfig]("auth"),
    mongoServer = config.as[MongoDbConfig]("mongo")
  )
}

case class RestServerConfig(
    host: String,
    port: Int,
    ssl: Boolean)

case class ActorSystemConfig(name: String)

case class AuthConfig(
    enabled: Boolean,
    serverUrl: String,
    clientId: String,
    clientSecret: String)

case class MongoDbConfig(host: String, port: Int, sharedDB: String)

trait ConfigurationComponent {
  def config: AppConfig
}
