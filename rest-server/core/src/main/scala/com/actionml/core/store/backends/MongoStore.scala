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
import java.util.concurrent.TimeUnit

import com.actionml.core.store.indexes.annotations.Indexed
import com.actionml.core.store.{DAO, Ordering, Store}
import com.mongodb.client.model.IndexOptions
import com.typesafe.scalalogging.LazyLogging
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


class MongoStorage(db: MongoDatabase, codecs: List[CodecProvider]) extends Store with LazyLogging {
  import MongoStorage.codecRegistry

  import scala.concurrent.ExecutionContext.Implicits.global

  override def createDao[T: TypeTag](name: String)(implicit ct: ClassTag[T]): DAO[T] = {
    // get information about indexes from annotations of DAO's type
    def getRequiredIndexesInfo: List[(String, Indexed)] = {
      val symbol = symbolOf[T]
      if (symbol.isClass) {
        symbol.asClass.primaryConstructor.typeSignature.paramLists.head
          .collect {
            case f if f.annotations.nonEmpty && f.annotations.forall(_.tree.tpe =:= typeOf[Indexed]) =>
              val i = f.annotations.head.tree.children.tail.foldLeft(Indexed()) {
                case (acc, Select(t, n)) if t.tpe <:< typeOf[DurationConversions] =>
                  t match {
                    case Apply(_, args) =>
                      acc.copy(ttl = Duration(s"${args.head} ${n.decodedName}"))
                  }
                case (acc, a@Select(t, n)) if a.tpe =:= typeOf[com.actionml.core.store.Ordering.Value] =>
                  util.Try(Ordering.withName(a.name.toString)).toOption.fold(acc)(o => acc.copy(order = o))
                case (acc, a@Select(t, n)) if a.tpe =:= typeOf[Duration] =>
                  acc
                case (acc, _) =>
                  acc
              }
              (f.name.toString, i)
          }
      } else List.empty
    }
    // get information about indexes from mongo db
    def getActualIndexesInfo(col: MongoCollection[T]): Future[Seq[(String, Indexed)]] = {
      col.listIndexes().map { i =>
        val keyInfo = i.get("key").get.asDocument()
        val iName = keyInfo.getFirstKey
        (iName, Indexed())
      }.toFuture
    }

    val collection = db.getCollection[T](name).withCodecRegistry(codecRegistry(codecs)(ct))
    // compare actual and required indexes and create missing ones
    getActualIndexesInfo(collection)
      .map(actualIndexesInfo => getRequiredIndexesInfo diff actualIndexesInfo.toList)
      .flatMap { absentIndexes =>
        val newIndexes = absentIndexes.map { case (iName, Indexed(iOrder, ttl)) =>
          val options = new IndexOptions()
          if (ttl.isFinite()) options.expireAfter(ttl.toMillis, TimeUnit.MILLISECONDS)
          IndexModel(Document(iName -> 1), options)
        }
        if (newIndexes.nonEmpty) {
          logger.info(s"Creating indexes ${newIndexes.map(i => s"${i.getKeys} - ${i.getOptions}")} for collection ${collection.namespace.getFullName}")
          collection.createIndexes(newIndexes).toFuture
        } else Future.successful(())
      }.recover {
      case e: Exception =>
        logger.error(s"Can't create indexes for ${collection.namespace.getFullName}", e)
    }
    new MongoDao[T](collection)
  }

  override def removeCollection(name: String): Unit = sync(removeCollectionAsync(name))

  override def drop(): Unit = sync(dropAsync)

  override def removeCollectionAsync(name: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug(s"Trying to removeOne collection $name from database ${db.name}")
    db.getCollection(name).drop.headOption().flatMap {
      case Some(_) =>
        logger.debug(s"Collection $name successfully removed from database ${db.name}")
        Future.successful(())
      case None =>
        logger.debug(s"Failure. Collection $name can't be removed from database ${db.name}")
        Future.failed(new RuntimeException(s"Can't removeOne collection $name"))
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

  override def dbName: String = db.name
}

object MongoStorage extends LazyLogging {
  lazy val uri = s"mongodb://${MongoConfig.mongo.host}:${MongoConfig.mongo.port}"
  private lazy val mongoClient = MongoClient(uri)


  def close = {
    logger.info(s"Closing mongo client $mongoClient")
    mongoClient.close()
  }

  def getStorage(dbName: String, codecs: List[CodecProvider]) = new MongoStorage(mongoClient.getDatabase(dbName), codecs)

  def codecRegistry(codecs: List[CodecProvider])(implicit ct: ClassTag[_]) = {
    import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
    import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY

    import scala.collection.JavaConversions._
    if (codecs.nonEmpty) fromRegistries(
      CodecRegistries.fromCodecs(new InstantCodec, new OffsetDateTimeCodec),
      fromProviders(codecs),
      DEFAULT_CODEC_REGISTRY
    ) else fromRegistries(
      CodecRegistries.fromCodecs(new InstantCodec, new OffsetDateTimeCodec),
      DEFAULT_CODEC_REGISTRY
    )
  }
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
