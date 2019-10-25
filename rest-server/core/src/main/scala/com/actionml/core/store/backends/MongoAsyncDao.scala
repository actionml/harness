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
import com.actionml.core.store.indexes.annotations.{CompoundIndex, SingleIndex}
import com.actionml.core.store.{DaoQuery, OrderBy, SyncDao}
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonInt32, BsonValue}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Sorts}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.control.NonFatal


class MongoAsyncDao[T: TypeTag](val collection: MongoCollection[T])(implicit ct: ClassTag[T])
  extends SyncDao[T]
    with LazyLogging
    with IndexesSupport {
  import DaoQuery.syntax._

  override def name = collection.namespace.getFullName
  override def dbName = collection.namespace.getDatabaseName
  override def collectionName: String = collection.namespace.getCollectionName

  override def findOneByIdAsync(id: String): Future[Option[T]] = {
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
        logger.trace(s"Successfully inserted $o into $name with result $t")
        Future.successful ()
      case None =>
        logger.error(s"Can't insert value $o to collection ${collection.namespace}")
        Future.failed(new RuntimeException(s"Can't insert value $o to collection ${collection.namespace}"))
    }
  }

  override def insertManyAsync(c: Seq[T])(implicit ec: ExecutionContext): Future[Unit] = {
    if (c.nonEmpty) {
      collection.insertMany(c).headOption.flatMap {
        case Some(t) =>
          logger.trace(s"Successfully inserted ${c.size} items into $name with result $t")
          Future.successful()
        case None =>
          logger.error(s"Can't insert $c into collection ${collection.namespace}")
          Future.failed(new RuntimeException(s"Can't insert $c to collection ${collection.namespace}"))
      }
    } else {
      logger.warn(s"No items inserted - $c")
      Future.successful ()
    }
  }

  override def updateAsync(filter: (String, QueryCondition)*)(o: T)(implicit ec: ExecutionContext): Future[T] = {
    collection.findOneAndReplace(mkFilter(filter), o).headOption.flatMap {
      case Some(t) =>
        logger.trace(s"Successfully updated object $o with the filter $filter")
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
    } yield ()).map(_ => logger.trace(s"Object $o with id $id (filter: $filter) saved successfully into $name"))
      .recover { case e => logger.error(s"Can't saveOneById object $o with id $id (filter $filter) into $name", e)}
  }

  override def saveOneAsync(query: (String, QueryCondition), o: T)(implicit ec: ExecutionContext): Future[Unit] = {
    val filter = mkFilter(Seq(query))
    (for {
      opt <- collection.find(filter).headOption
      _ <- if (opt.isDefined) collection.replaceOne(filter, o).headOption.recover {
        case e => logger.error(s"Can't replace object $o", e)
      } else insertAsync(o)
    } yield ()).map(_ => logger.trace(s"Object $o (filter: $filter) saved successfully into $name"))
      .recover { case e => logger.error(s"Can't saveOne object $o (filter $filter) into $name", e)}
  }

  override def removeOneAsync(filter: (String, QueryCondition)*)(implicit ec: ExecutionContext): Future[T] = {
    collection.findOneAndDelete(mkFilter(filter)).headOption.flatMap {
      case Some(t) =>
        logger.trace(s"$filter was successfully removed from collection $collection with namespace ${collection.namespace}. Result: $t")
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
        logger.trace(s"$filter objects successfully removed from collection $collection with namespace ${collection.namespace}. Delete Result: $t")
        Future.successful[Unit]()
      case None =>
        logger.error(s"Can't removeMany from collection ${collection.namespace} with filter $filter")
        Future.failed(new RuntimeException(s"Can't removeMany from collection ${collection.namespace} with filter $filter"))
    }
  }

  // compare actual and required indexes and create missing ones
  override def createIndexesAsync(ttl: Duration): Future[Unit] = {
    def actualTtl(indexName: String, indexes: Seq[(String, annotations.Index, Option[Duration])]): Option[Duration] = {
      indexes.find {
        case (i, SingleIndex(_, true), duration) if i == indexName => duration.isDefined
        case _ => false
      }.flatMap(_._3)
    }
    def mkIndexOptions(name: String) =
      if (name == "_id") IndexOptions().name(name)
      else IndexOptions().name(name).background(true)

    (for {
      actualIndexesInfo <- getActualIndexesInfo(collection)
      required = getRequiredIndexesInfo[T].map {
        case (name, index: SingleIndex) => (name, index, Option(ttl).filter(_ => index.isTtl))
        case (name, index: CompoundIndex) => (name, index, None)
      }
      absentIndexes = required diff actualIndexesInfo
      newIndexes = absentIndexes.collect {
        case (iName, SingleIndex(o, isTtl), _) if isTtl && actualTtl(iName, actualIndexesInfo).forall(_.compareTo(ttl) != 0) =>
          val options = mkIndexOptions(iName).expireAfter(ttl.toMillis, TimeUnit.MILLISECONDS)
          (iName, IndexModel(Document(iName -> order2Int(o)), options))
        case (iName, SingleIndex(o, _), _)=>
          (iName, IndexModel(Document(iName -> order2Int(o)), mkIndexOptions(iName)))
        case (_, CompoundIndex(fields), _) =>
          val indexName: String = fields.map(_._1).mkString("_")
          val builder = Document.builder
          fields.foreach {
            case (name, o) =>
              builder += name -> ordering2BsonValue(o)
          }
          (indexName, IndexModel(builder.result(), mkIndexOptions(indexName)))
      }
      _ <- Future.traverse(newIndexes.map(_._1) intersect actualIndexesInfo.map(_._1)) { iname =>
        logger.info(s"Drop index $iname")
        collection.dropIndex(iname).toFuture
          .recover {
            case NonFatal(e) =>
              logger.error(s"Can't drop index $iname", e)
          }
      }
      _ <- if (newIndexes.nonEmpty) {
        logger.info(s"Create indexes ${newIndexes.map(i => s"${i._2.getKeys} - ${i._2.getOptions}")} for collection ${collection.namespace.getFullName}")
        collection.createIndexes(newIndexes.map(_._2)).toFuture.map(_ => ())
      } else Future.successful(())
    } yield ())
      .recover {
        case NonFatal(e) =>
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
      case store.Ordering.asc => Sorts.ascending(order.fieldNames: _*)
      case store.Ordering.desc => Sorts.descending(order.fieldNames: _*)
    }
  }

  private def ordering2BsonValue: store.Ordering.Ordering => BsonValue = {
      case store.Ordering.asc => new BsonInt32(1)
      case store.Ordering.desc => new BsonInt32(-1)
  }

  private def order2Int(ordering: store.Ordering.Ordering): Int = {
    ordering match {
      case store.Ordering.asc => 1
      case store.Ordering.desc => -1
    }
  }
}
