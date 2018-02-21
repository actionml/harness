package com.actionml.authserver.config

import com.actionml.authserver.model.{Client, RoleSet}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase


/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 19:09
  */
case class AppConfig(authServer: AuthServerConfig, actorSystem: ActorSystemConfig)

object AppConfig {
  lazy val root = ConfigFactory.load()

  def apply: AppConfig = new AppConfig(
    authServer = root.as[AuthServerConfig]("auth-server"),
    actorSystem = root.as[ActorSystemConfig]("actor-system")
  )
}

case class AuthServerConfig(
  host: String,
  port: Int = 9099,
  sslEnabled: Boolean,
  mongoDb: MongoDbConfig,
  accessTokenTtl: Long = 2 * 60 * 60 * 1000,
  authorizationEnabled: Boolean,
  clients: List[Client],
  roleSets: List[RoleSet])

case class ActorSystemConfig(name: String)

case class MongoDbConfig(uri: String, dbName: String)
