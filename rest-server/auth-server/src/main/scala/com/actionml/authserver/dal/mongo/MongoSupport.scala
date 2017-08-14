package com.actionml.authserver.dal.mongo

import com.actionml.authserver.config.AppConfig
import org.mongodb.scala.{MongoClient, MongoDatabase}

trait MongoSupport {
  def mongoClient: MongoClient = MongoSupport.mongoClient
  def mongoDb: MongoDatabase = MongoSupport.mongoDatabase
}

object MongoSupport {
  private val config = AppConfig.apply.authServer.mongoDb

  private lazy val mongoClient = MongoClient(config.uri)
  private lazy val mongoDatabase = mongoClient.getDatabase(config.dbName)
}
