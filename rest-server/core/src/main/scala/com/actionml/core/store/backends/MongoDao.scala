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

import java.util.concurrent.TimeUnit

import com.actionml.core.store
import com.actionml.core.store.DaoQuery.QueryCondition
import com.actionml.core.store.indexes.annotations
import com.actionml.core.store.indexes.annotations.Indexed
import com.actionml.core.store.{DAO, DaoQuery, OrderBy}
import com.mongodb.client.model.IndexOptions
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Sorts}
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

import org.mongodb.scala.model.{Filters, IndexModel, Sorts}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


class MongoDao[T: TypeTag](val collection: MongoCollection[T])(implicit ct: ClassTag[T])
  extends DAO[T]
    with LazyLogging
    with IndexesSupport {
  import DaoQuery.syntax._
  import scala.concurrent.ExecutionContext.Implicits.global

  import scala.concurrent.ExecutionContext.Implicits.global

  override def name = collection.namespace.getFullName
  override def dbName = collection.namespace.getDatabaseName
  override def collectionName: String = collection.namespace.getCollectionName

  override def findOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[Option[T]] = {
    findOneAsync("_id" === id)
  }

  override def findOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Option[T]] = {
    collection.find(mkFilter(filter)).headOption
  }

  override def findManyAsync(query: DaoQuery)(implicit ec: ExecutionContext): Future[Iterable[T]] = {
    val find = collection.find(mkFilter(query.filter))
    query.orderBy.fold(find) { order =>
      find.sort(order2Bson(order))
    }.skip(query.offset)
     .limit(query.limit)
     .toFuture
  }

  override def insertAsync(o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.insertOne(o).headOption.flatMap {
      case Some(t) =>
        logger.info(s"Successfully inserted $o into $name with result $t")
        Future.successful ()
      case None =>
        logger.error(s"Can't insert value $o to collection ${collection.namespace}")
        Future.failed(new RuntimeException(s"Can't insert value $o to collection ${collection.namespace}"))
    }
  }

  override def insertManyAsync(c: Seq[T])(implicit ec: ExecutionContext): Future[Unit] = {
    collection.insertMany(c).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"Successfully inserted many into $name with result $t")
        Future.successful ()
      case None =>
        logger.error(s"Can't insert many into collection ${collection.namespace}")
        Future.failed(new RuntimeException(s"Can't insert many to collection ${collection.namespace}"))
    }
  }

  override def updateAsync(filter: (String, QueryCondition)*)(o: T)(implicit ec: ExecutionContext): Future[T] = {
    collection.findOneAndReplace(mkFilter(filter), o).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"Successfully updated object $o with the filter $filter")
        Future.successful(t)
      case None =>
        logger.error(s"Can't update collection ${collection.namespace} with filter $filter and value $o")
        Future.failed(new RuntimeException(s"Can't update collection ${collection.namespace} with filter $filter and value $o"))
    }
  }

  override def saveOneByIdAsync(id: String, o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    val filter = mkFilter(Seq("_id" === id))
    (for {
      opt <- collection.find(filter).headOption
      _ <- if (opt.isDefined) collection.replaceOne(filter, o).headOption.recover {
        case e => logger.error(s"Can't replace object $o", e)
      } else insertAsync(o)
    } yield ()).map(_ => logger.debug(s"Object $o with id $id (filter: $filter) saved successfully into $name"))
      .recover { case e => logger.error(s"Can't saveOneById object $o with id $id (filter $filter) into $name", e)}
  }

  override def saveOneAsync(query: (String, QueryCondition), o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    val filter = mkFilter(Seq(query))
    (for {
      opt <- collection.find(filter).headOption
      _ <- if (opt.isDefined) collection.replaceOne(filter, o).headOption.recover {
        case e => logger.error(s"Can't replace object $o", e)
      } else insertAsync(o)
    } yield ()).map(_ => logger.info(s"Object $o (filter: $filter) saved successfully into $name"))
      .recover { case e => logger.error(s"Can't saveOne object $o (filter $filter) into $name", e)}
  }

  override def removeOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[T] = {
    collection.findOneAndDelete(mkFilter(filter)).headOption.flatMap {
      case Some(t) =>
        logger.debug(s"$filter was successfully removed from collection $collection with namespace ${collection.namespace}. Result: $t")
        Future.successful(t)
      case None =>
        logger.error(s"Can't removeOne from collection ${collection.namespace} with filter $filter")
        Future.failed(new RuntimeException(s"Can't removeOne from collection ${collection.namespace} with filter $filter"))
    }
  }

  override def removeOneByIdAsync(id: String)(implicit ec: ExecutionContext): Future[T] = {
    removeOneAsync("_id" === id)
  }

  override def removeManyAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[Unit] = {
    collection.deleteMany(mkFilter(filter)).headOption().flatMap {
      case Some(t) =>
        logger.debug(s"$filter objects successfully removed from collection $collection with namespace ${collection.namespace}. Delete Result: $t")
        Future.successful[Unit]()
      case None =>
        logger.error(s"Can't removeMany from collection ${collection.namespace} with filter $filter")
        Future.failed(new RuntimeException(s"Can't removeMany from collection ${collection.namespace} with filter $filter"))
    }
  }

  // compare actual and required indexes and create missing ones
  override def createIndexesAsync(ttl: Duration): Future[Unit] = {
    def actualTtl(indexName: String, indexes: Seq[(String, annotations.Indexed, Option[Duration])]): Option[Duration] = {
      indexes.find {
        case (i, Indexed(_, true), duration) if i == indexName => duration.isDefined
        case _ => false
      }.flatMap(_._3)
    }
    (for {
      actualIndexesInfo <- getActualIndexesInfo(collection)
      required = getRequiredIndexesInfo[T].map { case (a, b) => (a, b, Option(ttl).filter(_ => b.isTtl))}
      absentIndexes = required diff actualIndexesInfo
      newIndexes = absentIndexes.collect {
        case (iName, Indexed(o, isTtl), _) if isTtl && actualTtl(iName, actualIndexesInfo).forall(_.compareTo(ttl) != 0) =>
          val options = new IndexOptions().name(iName)
            .expireAfter(ttl.toMillis, TimeUnit.MILLISECONDS)
          (iName, IndexModel(Document(iName -> order2Int(o)), options))
        case (iName, Indexed(o, _), _)=>
          (iName, IndexModel(Document(iName -> order2Int(o)), new IndexOptions().name(iName)))
      }
      _ <- Future.traverse(newIndexes.map(_._1) intersect actualIndexesInfo.map(_._1)) { iname =>
        logger.info(s"Drop index $iname")
        collection.dropIndex(iname).toFuture
          .recover {
            case e: Exception =>
              logger.error(s"Can't drop index $iname", e)
          }
      }
      _ <- if (newIndexes.nonEmpty) {
        logger.info(s"Create indexes ${newIndexes.map(i => s"${i._2.getKeys} - ${i._2.getOptions}")} for collection ${collection.namespace.getFullName}")
        collection.createIndexes(newIndexes.map(_._2)).toFuture.map(_ => ())
      } else Future.successful(())
    } yield ())
      .recover {
        case e: Exception =>
          logger.error(s"Can't create indexes for ${collection.namespace.getFullName}", e)
      }
  }


  private def mkFilter(fields: Seq[(String, QueryCondition)]): Bson = {
    import DaoQuery._
    if (fields.isEmpty) Document("_id" -> Document("$exists" -> true))
    else Filters.and(fields.map {
      case (k, GreaterOrEqualsTo(v)) => Filters.gte(k, v)
      case (k, GreaterThen(v)) => Filters.gt(k, v)
      case (k, LessOrEqualsTo(v)) => Filters.lte(k, v)
      case (k, LessThen(v)) => Filters.lt(k, v)
      case (k, Equals(v)) => Filters.eq(k, v)
    }.toArray[Bson]: _*)
  }

  private def order2Bson(order: OrderBy): Bson = {
    order.ordering match {
      case com.actionml.core.store.Ordering.asc => Sorts.ascending(order.fieldNames: _*)
      case com.actionml.core.store.Ordering.desc => Sorts.descending(order.fieldNames: _*)
    }
  }

  private def order2Int(ordering: store.Ordering.Ordering): Int = {
    ordering match {
      case com.actionml.core.store.Ordering.asc => 1
      case com.actionml.core.store.Ordering.desc => -1
    }
  }
}
