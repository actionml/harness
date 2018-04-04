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

import com.actionml.core.storage.{DAO, Storage}
import org.bson.codecs.configuration.CodecProvider
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class MongoStorage(db: MongoDatabase, codecs: List[CodecProvider]) extends Storage {

  override def createDao[T](name: String)(implicit ct: ClassTag[T]): DAO[T] = {
    import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
    import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY

    import scala.collection.JavaConversions._
    val codecRegistry = fromRegistries(fromProviders(codecs), DEFAULT_CODEC_REGISTRY)
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


class MongoDao[T](collection: MongoCollection[T])(implicit ct: ClassTag[T]) extends DAO[T] {

  override def find(filter: (String, Any)*): Future[Option[T]] =
    collection.find(mkBson(filter)).headOption

  override def list(filter: (String, Any)*): Future[Iterable[T]] =
    collection.find(mkBson(filter)).toFuture

  override def insert(o: T)(implicit ec: ExecutionContext): Future[Unit] =
    collection.insertOne(o).toFuture.map(_ => ())

  override def update(filter: (String, Any)*)(o: T): Future[T] = {
    collection.findOneAndReplace(mkBson(filter), o).toFuture
  }

  override def remove(filter: (String, Any)*): Future[T] =
    collection.findOneAndDelete(mkBson(filter)).toFuture


  private def mkBson(fields: Seq[(String, Any)]): Bson = {
    Filters.and(fields.map { case (k, v) => Filters.eq(k, v) }.toArray: _*)
  }
}
