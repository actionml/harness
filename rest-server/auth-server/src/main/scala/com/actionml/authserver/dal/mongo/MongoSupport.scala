package com.actionml.authserver.dal.mongo

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.model.{Client, Permission, UserAccount}
import com.mongodb.async.client.MongoClientSettings
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala._
import org.mongodb.scala.{MongoClient, MongoDatabase, ServerAddress}

import scala.reflect.ClassTag

trait MongoSupport {
  def collection[T](name: String)(implicit ct: ClassTag[T]) = {
    MongoSupport.mongoDatabase.getCollection[T](name)
  }
}

object MongoSupport {
  private val config = AppConfig.apply.authServer.mongoDb

  import scala.collection.JavaConversions._
  private val settings = MongoClientSettings.builder
    .clusterSettings(ClusterSettings.builder().hosts(List(ServerAddress("localhost"))).build)
    .codecRegistry(codecRegistry)
    .build
  private lazy val mongoClient = MongoClient(settings)
  private lazy val mongoDatabase = mongoClient.getDatabase(config.dbName)

  import org.mongodb.scala.bson.codecs.Macros._
  import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
  import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders}
  private lazy val codecRegistry = fromRegistries(
    DEFAULT_CODEC_REGISTRY,
    fromProviders(classOf[Client], classOf[UserAccount], classOf[Permission])
  )
}
