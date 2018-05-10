package com.actionml.authserver.dal.mongo

import java.time.Instant

import com.actionml.authserver.config.AppConfig
import com.actionml.authserver.model._
import com.mongodb.async.client.MongoClientSettings
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClient, ServerAddress}

import scala.reflect.ClassTag

trait MongoSupport {
  def collection[T](name: String)(implicit ct: ClassTag[T]) = {
    MongoSupport.mongoDatabase.getCollection[T](name)
  }
}

object MongoSupport {
  def close = mongoClient.close()

  private val config = AppConfig.apply.authServer.mongoDb

  import scala.collection.JavaConversions._
  private val settings = MongoClientSettings.builder
    .clusterSettings(ClusterSettings.builder().hosts(List(ServerAddress("localhost"))).build)
    .codecRegistry(codecRegistry)
    .build
  private val mongoClient = MongoClient(settings)
  private val mongoDatabase = mongoClient.getDatabase(config.dbName)

  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._
  private lazy val codecRegistry = fromRegistries(
    CodecRegistries.fromCodecs(new InstantCodec),
    /*
     looks like list inside fromProviders should be in usage order (accessToken and userAccount contains permissions, so,
     permissions should be declared first.
     It also can correspond to https://jira.mongodb.org/browse/SCALA-338
     */
    fromProviders(classOf[Client], classOf[RoleSet], classOf[Permission], classOf[AccessToken], classOf[UserAccount]),
    DEFAULT_CODEC_REGISTRY
  )

  class InstantCodec extends Codec[Instant] {
    override def decode(reader: BsonReader, dc: DecoderContext) = Instant.ofEpochMilli(reader.readDateTime)
    override def encode(writer: BsonWriter, value: Instant, ec: EncoderContext) = writer.writeDateTime(value.toEpochMilli)
    override def getEncoderClass = classOf[Instant]
  }
}
