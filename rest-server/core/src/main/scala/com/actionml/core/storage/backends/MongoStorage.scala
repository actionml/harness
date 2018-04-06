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

package com.actionml.core.storage.backends

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.actionml.core.storage.{DAO, Storage}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Completed, MongoClient, MongoCollection, MongoDatabase, Observer}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class MongoStorage(db: MongoDatabase, codecs: List[CodecProvider]) extends Storage {

  override def createDao[T](name: String)(implicit ct: ClassTag[T]): DAO[T] = {
    import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
    import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY

    import scala.collection.JavaConversions._
    val codecRegistry = fromRegistries(
      CodecRegistries.fromCodecs(new InstantCodec, new OffsetDateTimeCodec),
      fromProviders(codecs),
      DEFAULT_CODEC_REGISTRY
    )
    new MongoDao[T](db.getCollection[T](name).withCodecRegistry(codecRegistry))
  }

  override def removeCollection(name: String)(implicit ec: ExecutionContext): Future[Unit] = {
    db.getCollection(name).drop.toFuture.map(_ => ())
  }

  override def drop()(implicit ec: ExecutionContext): Future[Unit] = db.drop.toFuture.map(_ => ())
}

object MongoStorage {

  def getStorage(dbName: String, codecs: List[CodecProvider]) = new MongoStorage(MongoClient("mongodb://localhost:27017").getDatabase(dbName), codecs)
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

class MongoDao[T](collection: MongoCollection[T])(implicit ct: ClassTag[T]) extends DAO[T] {

  override def find(filter: (String, Any)*): Future[Option[T]] =
    collection.find(mkBson(filter)).headOption

  override def list(filter: (String, Any)*): Future[Iterable[T]] =
    collection.find(mkBson(filter)).toFuture

  override def insert(o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    val s = collection.insertOne(o)
    s.subscribe(new Observer[Completed] {
      override def onNext(result: Completed): Unit = println(s"onNext: $result")
      override def onError(e: Throwable): Unit = println(s"onError: $e")
      override def onComplete(): Unit = println("onComplete")
    })
    val f = s.headOption
    f.onFailure {
      case e => e.printStackTrace
    }
    f.map(_ => ())
  }

  override def update(filter: (String, Any)*)(o: T): Future[T] =
    collection.findOneAndReplace(mkBson(filter), o).toFuture

  override def upsert(filter: (String, Any)*)(o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      opt <- collection.find(mkBson(filter)).headOption
      _ <- if (opt.isDefined) collection.replaceOne(mkBson(filter), o).headOption.recover { case e => e.printStackTrace }
           else insert(o)
    } yield ()
  }

  override def remove(filter: (String, Any)*): Future[T] =
    collection.findOneAndDelete(mkBson(filter)).toFuture


  private def mkBson(fields: Seq[(String, Any)]): Bson = {
    if (fields.isEmpty) Document("_id" -> Document("$exists" -> true))
    else Filters.and(fields.map { case (k, v) => Filters.eq(k, v) }.toArray: _*)
  }
}
