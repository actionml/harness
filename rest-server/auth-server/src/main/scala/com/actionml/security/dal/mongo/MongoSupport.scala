package com.actionml.security.dal.mongo

import com.actionml.router.config.AppConfig
import org.mongodb.scala.{MongoClient, MongoDatabase}

trait MongoSupport {
  import MongoSupport._

  def mongoDb: MongoDatabase = mongoDatabase
}

object MongoSupport {
  private val config = AppConfig.apply.authServer.mongoDb

  private lazy val mongoClient = MongoClient(config.uri)
  private lazy val mongoDatabase = mongoClient.getDatabase(config.dbName)
}
