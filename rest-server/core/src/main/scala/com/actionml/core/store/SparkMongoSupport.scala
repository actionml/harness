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

package com.actionml.core.store

import com.actionml.core.spark.GenericMongoConnector
import com.actionml.core.store.backends.{MongoConfig, MongoStorage}
import com.mongodb.spark.MongoSpark
import com.mongodb.spark.config.{ReadConfig, WriteConfig}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bson.codecs.configuration.CodecProvider

import scala.reflect.ClassTag


trait SparkMongoSupport extends SparkStoreSupport {

  override private[store] def readRdd[T: ClassTag](
    sc: SparkContext,
    dbHost: String = "localhost",
    codecs: List[CodecProvider] = List.empty,
    dbName: Option[String] = None,
    colName: Option[String] = None): RDD[T] = {
    val ct = implicitly[ClassTag[T]]
    if (dbName.isDefined && colName.isDefined) {
      // not sure if the codecs are understood here--I bet not
      val rc = ReadConfig(databaseName = dbName.get, collectionName = colName.get)
      MongoSpark
        .builder()
        .sparkContext(sc)
        .readConfig(rc)
        .connector(new GenericMongoConnector(dbHost, codecs, ct))
        .build
        .toRDD()
    } else {
      MongoSpark
        .builder()
        .sparkContext(sc)
        .connector(new GenericMongoConnector(dbHost, codecs, ct))
        .build
        .toRDD()
    }
  }
}

object SparkMongoSupport {
  object syntax {
    implicit class DaoSparkOps[D <: DAO[_]](dao: D) {
      def readRdd[T: ClassTag](codecs: List[CodecProvider] = List.empty)(implicit sc: SparkContext): RDD[T] = {
        val ct = implicitly[ClassTag[T]]
        val rc = ReadConfig(databaseName = dao.dbName, collectionName = dao.collectionName)
        MongoSpark
          .builder()
          .sparkContext(sc)
          .readConfig(rc)
          .connector(new GenericMongoConnector(MongoConfig.mongo.host, codecs, ct))
          .build
          .toRDD()
      }
    }

    implicit class RddMongoOps[D <: DAO[_]](dao: D) {
      def writeToMongo(rdd: RDD[_]): Unit = {
        val writeConfig = WriteConfig(
          databaseName = dao.dbName,
          collectionName = dao.collectionName,
          connectionString = Some(MongoStorage.uri),
          replaceDocument = true,
          forceInsert = true
        )
        MongoSpark.save(rdd, writeConfig)
      }
    }
  }
}
