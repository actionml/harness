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

import java.io.File

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.store.backends.MongoStorage
import com.actionml.core.validate._
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._


trait SparkContextSupport extends LazyLogging {
  import com.actionml.core.spark.SparkContextSupport._

  def createSparkContext(appName: String, dbName: String, collection: String, config: String): Validated[ValidateError, SparkContext] = {
    val configMap = parseAndValidate[Map[String, String]](config, transform = _ \ "sparkConf")
    configMap.get("master").map { master =>
      SparkMongoConfig(master, appName, dbName, collection, configMap - "master")
    }.map(Valid(_)).getOrElse(Invalid(ParseError("Wrong format at sparkConf field")))
  }.andThen { sparkConfig =>
    try {
      val dbUri = MongoStorage.uri
      val conf = new SparkConf()
        .setMaster(sparkConfig.master)
        .setAppName(sparkConfig.appName)
      conf.set("deploy-mode", "cluster")
      conf.set("spark.mongodb.input.uri", dbUri)
      conf.set("spark.mongodb.input.database", sparkConfig.database)
      conf.set("spark.mongodb.input.collection", sparkConfig.collection)
      conf.setAll(sparkConfig.properties)
      Valid(new SparkContext(conf))
    } catch {
      case e: Exception =>
        logger.error("Can't create spark context", e)
        Invalid(ValidRequestExecutionError("Can't create spark context"))
    }
  }

  def createSparkContext(appName: String, config: String): Validated[ValidateError, SparkContext] = {
    val configMap = parseAndValidate[Map[String, String]](config, transform = _ \ "sparkConf")
    configMap.get("master").map { master =>
      import org.joda.time.format.DateTimeFormat
      import org.joda.time.format.DateTimeFormatter
      val fmt = DateTimeFormat.forPattern("YYYY/MM/dd-HH:mm:ss")
      val now = fmt.print(DateTime.now)

      SparkSimpleConfig(master, appName + " " + now, configMap - "master")
    }.map(Valid(_)).getOrElse(Invalid(ParseError("Wrong format at sparkConf field")))
  }.andThen { sparkConfig =>
    try {
      val conf = new SparkConf()
        .setMaster(sparkConfig.master)
        .setAppName(sparkConfig.appName)
        .setAll(sparkConfig.properties.filterNot(_._1 == Settings.jarsDir))

      sparkConfig.properties.get(Settings.jarsDir).foreach { dir =>
        conf.setJars(new File(dir).listFiles().filter(f => f.isFile && f.getName.endsWith(".jar")).map("file://" + _.getAbsolutePath))
      }
      Valid(new SparkContext(conf))
    } catch {
      case e: Exception =>
        logger.error("Can't create spark context", e)
        Invalid(ValidRequestExecutionError("Can't create spark context"))
    }
  }

  private def parseAndValidate[T](jsonStr: String, transform: JValue => JValue = a => a)(implicit mf: Manifest[T]): T = {
    implicit val _ = org.json4s.DefaultFormats
    transform(parse(jsonStr)).extract[T]
  }
}

object SparkContextSupport {
  object Settings {
    val jarsDir = "jars.dir"
  }

  private case class SparkMongoConfig(master: String, appName: String, database: String, collection: String, properties: Map[String, String])
  private case class SparkSimpleConfig(master: String, appName: String, properties: Map[String, String])
}
