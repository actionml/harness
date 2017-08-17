package com.actionml.authserver.dal.mongo

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.model.{Client, Permission, UserAccount}
import com.mongodb.async.client.MongoClientSettings
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala._
import org.mongodb.scala.{MongoClient, MongoDatabase, ServerAddress}

trait MongoSupport {
  def mongoClient: MongoClient = MongoSupport.mongoClient
  def mongoDb: MongoDatabase = MongoSupport.mongoDatabase
}

object MongoSupport {
  private val config = AppConfig.apply.authServer.mongoDb

  import scala.collection.JavaConversions._
  private val settings = MongoClientSettings.builder
    .clusterSettings(ClusterSettings.builder().hosts(List(ServerAddress(config.uri))).build)
    .codecRegistry(codecRegistry)
    .build
  private lazy val mongoClient = MongoClient(config.uri)
  private lazy val mongoDatabase = mongoClient.getDatabase(config.dbName).withCodecRegistry(codecRegistry)

  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
  import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders}
  private lazy val codecRegistry = fromRegistries(
    fromProviders(classOf[Client], classOf[UserAccount], classOf[Permission]),
    DEFAULT_CODEC_REGISTRY
  )
}
