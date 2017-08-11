package com.actionml.router.config

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
case class AppConfig(authServer: HttpServerConfig, actorSystem: ActorSystemConfig)

object AppConfig {
  private lazy val config = ConfigFactory.load()

  def apply: AppConfig = new AppConfig(
    authServer = config.as[HttpServerConfig]("auth-server"),
    actorSystem = config.as[ActorSystemConfig]("actor-system")
  )
}

case class HttpServerConfig(host: String, port: Int, ssl: Boolean, mongoDb: MongoDbConfig)

case class ActorSystemConfig(name: String)

case class MongoDbConfig(uri: String, dbName: String)
