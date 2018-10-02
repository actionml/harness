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

package com.actionml.core.spark

import com.actionml.core.store.backends.MongoStorage
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase
import com.mongodb.spark.config.ReadConfig
import com.mongodb.spark.{MongoClientFactory, MongoConnector, MongoSpark}
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bson.codecs.configuration.CodecProvider

import scala.reflect.ClassTag

// todo: these should be put in the DAO as a mixin trait for Spark, in which case the params are all known or can be found
// leaving only the sc to be passed in perhaps implicitly
trait SparkStoreSupport {
  def readRdd[T: ClassTag](
    sc: SparkContext,
    dbHost: String,
    codecs: List[CodecProvider],
    dbName: Option[String] = None,
    collectionName: Option[String] = None): RDD[T]
}

trait SparkMongoSupport extends SparkStoreSupport {

  override def readRdd[T: ClassTag](
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

class GenericMongoConnector[T](host: String, codecs: List[CodecProvider], ct: ClassTag[T])
  extends MongoConnector(new GenericMongoClientFactory(host, codecs, ct))
    with Serializable {}

class GenericMongoClientFactory[T](host: String, codecs: List[CodecProvider], ct: ClassTag[T]) extends MongoClientFactory {
  override def create(): MongoClient = new GenericMongoClient[T](host, codecs, ct)
}

class GenericMongoClient[T](host: String, codecs: List[CodecProvider], ct: ClassTag[T]) extends MongoClient(host) with LazyLogging {

  override def getDatabase(databaseName: String): MongoDatabase =
    super.getDatabase(databaseName).withCodecRegistry(MongoStorage.codecRegistry(codecs)(ct))
}
