package com.actionml.config

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 19:09
  */
case class AppConfig(restServer: RestServerConfig)
case class RestServerConfig(
  host: String,
  port: Int
)

object AppConfig{
  val config = ConfigFactory.load()

  def apply: AppConfig = new AppConfig(
    restServer = config.as[RestServerConfig]("restServer")
  )


}
