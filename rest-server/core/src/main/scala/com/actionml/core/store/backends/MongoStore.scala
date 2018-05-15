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

package com.actionml.core.store.backends

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.actionml.core.store.{DAO, Store}
import com.typesafe.scalalogging.LazyLogging
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.{MongoClient, MongoDatabase}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag


class MongoStorage(db: MongoDatabase, codecs: List[CodecProvider]) extends Store with LazyLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def createDao[T](name: String)(implicit ct: ClassTag[T]): DAO[T] = {
    import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
    import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY

    import scala.collection.JavaConversions._
    val codecRegistry = if (codecs.nonEmpty) fromRegistries(
      CodecRegistries.fromCodecs(new InstantCodec, new OffsetDateTimeCodec),
      fromProviders(codecs),
      DEFAULT_CODEC_REGISTRY
    ) else fromRegistries(
      CodecRegistries.fromCodecs(new InstantCodec, new OffsetDateTimeCodec),
      DEFAULT_CODEC_REGISTRY
    )
    new MongoDao[T](db.getCollection[T](name).withCodecRegistry(codecRegistry))
  }

  override def removeCollection(name: String): Unit = sync(removeCollectionAsync(name))

  override def drop(): Unit = sync(dropAsync)

  override def removeCollectionAsync(name: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug(s"Trying to remove collection $name from database ${db.name}")
    db.getCollection(name).drop.headOption().flatMap {
        case Some(_) =>
          logger.debug(s"Collection $name successfully removed from database ${db.name}")
          Future.successful(())
        case None =>
          logger.debug(s"Failure. Collection $name can't be removed from database ${db.name}")
          Future.failed(new RuntimeException(s"Can't remove collection $name"))
      }
  }

  override def dropAsync()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug(s"Trying to drop database ${db.name}")
    db.drop.headOption.flatMap {
      case Some(_) =>
        logger.debug(s"Database ${db.name} was successfully dropped")
        Future.successful(())
      case None =>
        logger.debug(s"Can't drop database ${db.name}")
        Future.failed(new RuntimeException("Can't drop db"))
    }
  }


  private val timeout = 5 seconds
  private def sync[A](f: => Future[A]): A = Await.result(f, timeout)
}

object MongoStorage extends LazyLogging {
  val uri = s"mongodb://${MongoConfig.mongo.host}:${MongoConfig.mongo.port}"
  private lazy val mongoClient = MongoClient(uri)


  def close = {
    logger.info(s"Closing mongo client $mongoClient")
    mongoClient.close()
  }

  def getStorage(dbName: String, codecs: List[CodecProvider]) = new MongoStorage(mongoClient.getDatabase(dbName), codecs)
}


class InstantCodec extends Codec[Instant] {
  override def decode(reader: BsonReader, dc: DecoderContext): Instant = Instant.ofEpochMilli(reader.readDateTime)
  override def encode(writer: BsonWriter, value: Instant, ec: EncoderContext): Unit = writer.writeDateTime(value.toEpochMilli)
  override def getEncoderClass: Class[Instant] = classOf[Instant]
}

class OffsetDateTimeCodec extends Codec[OffsetDateTime] {
  override def decode(reader: BsonReader, dc: DecoderContext): OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(reader.readDateTime), ZoneOffset.UTC)
  override def encode(writer: BsonWriter, value: OffsetDateTime, ec: EncoderContext): Unit = writer.writeDateTime(value.toInstant.toEpochMilli)
  override def getEncoderClass: Class[OffsetDateTime] = classOf[OffsetDateTime]
}
