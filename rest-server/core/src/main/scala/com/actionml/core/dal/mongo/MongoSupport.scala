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

package model.dal.mongo

import java.time.Instant

import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.ServerAddress

// import com.actionml.authserver.config.AppConfig
import com.actionml.core.model.User
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.connection.ClusterSettings
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.MongoClient
import com.actionml.core.config._

import scala.reflect.ClassTag

trait MongoSupport {
  def collection[T](dbName: Option[String], collectionName: String)(implicit ct: ClassTag[T]) = {
    //MongoSupport.mongoDatabase.getCollection[T](name)
    // If no DB Name is passed in use the shared one for all engines, this is useful for the shared user case
    // where all engines for a client or some globally shared user database is used
    MongoSupport.mongoClient.getDatabase(dbName.getOrElse(MongoSupport.config.sharedDB)).getCollection[T](collectionName)
  }
}

object MongoSupport {
  private val config = AppConfig.apply.mongoServer

  import scala.collection.JavaConversions._

  private val settings = MongoClientSettings.builder
    .clusterSettings(ClusterSettings.builder().hosts(List(ServerAddress(config.host, port = config.port))).build)
    .codecRegistry(codecRegistry)
    .build
  private val mongoClient = MongoClient(settings)

  // Todo: this should be set at runtime to the shared one in the Engine JSON or some global one
  //private val mongoDatabase = mongoClient.getDatabase(config.sharedDB)

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
    fromProviders(classOf[User]),
    DEFAULT_CODEC_REGISTRY
  )

  // todo: doens't work because the macros done work on a passed in class so need to pass in codecs
  private var codecRegistries2: CodecRegistry = fromRegistries(
    CodecRegistries.fromCodecs(new InstantCodec),
    /*
     looks like list inside fromProviders should be in usage order (accessToken and userAccount contains permissions, so,
     permissions should be declared first.
     It also can correspond to https://jira.mongodb.org/browse/SCALA-338
     */
    fromProviders(classOf[User]),
    DEFAULT_CODEC_REGISTRY
  )

  /* todo: doens't work because the macros done work on a passed in class so need to pass in codecs

  def registerCodec(clazz: Class[_]): Unit = {
    codecRegistries2 = fromRegistries(
      codecRegistries2,
      fromProviders(clazz)
    )
  }
  */

  class InstantCodec extends Codec[Instant] {
    override def decode(reader: BsonReader, dc: DecoderContext) = Instant.ofEpochMilli(reader.readDateTime)
    override def encode(writer: BsonWriter, value: Instant, ec: EncoderContext) = writer.writeDateTime(value.toEpochMilli)
    override def getEncoderClass = classOf[Instant]
  }
}
