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

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.Try


trait SparkContextSupport[A] {
  this: SparkStorageSupport[A] with LazyLogging =>
  import SparkContextSupport._

  def execute[B](fn: Iterator[A] => B, config: String, defaults: Map[String, String])(implicit ca: ClassTag[A], cb: ClassTag[B]): Future[Array[B]] = {
    getSparkContext(config, defaults)
      .map(sc => (sc, sc.runJob(createRdd(sc), fn)))
      .map { case (sc, value) =>
        sc.stop()
        value
      }
  }
}

object SparkContextSupport {

  private val state: AtomicReference[SparkContextState] = new AtomicReference(Idle)

  private def getSparkContext(config: String, defaults: Map[String, String]): Future[SparkContext] = {
    val params = SparkContextParams(config, defaults: Map[String, String])
    state.get match {
      case Idle =>
        Future.fromTry(createSparkContext(params))
      case Running(currentParams, p, _) if currentParams == params && p.isCompleted && p.future.value.forall(r => r.isSuccess && !r.get.isStopped) =>
        p.future
      case s@Running(_, p, promises) if p.isCompleted && p.future.value.forall(r => r.isSuccess && !r.get.isStopped) =>
        promises.getOrElse(params, {
          val p = Promise[SparkContext]()
          state.compareAndSet(s, s.copy(otherPromises = promises + (params -> p)))
          p
        }).future
    }
  }

  private def createSparkContext(params: SparkContextParams): Try[SparkContext] = Try {
    val configMap = params.defaults ++ parseAndValidate[Map[String, String]](params.config, transform = _ \ "sparkConf")
    val conf = new SparkConf()
    for {
      master <- configMap.get("master")
      appName <- configMap.get("appName")
    } conf.setMaster(master).setAppName(appName)
    conf.setAll(configMap -- Seq("master", "appName", "dbName", "collection"))
    new SparkContext(conf)
  }

  private def parseAndValidate[T](jsonStr: String, transform: JValue => JValue = a => a)(implicit mf: Manifest[T]): T = {
    implicit val _ = org.json4s.DefaultFormats
    transform(parse(jsonStr)).extract[T]
  }

  private case class SparkContextParams(config: String, defaults: Map[String, String])

  private sealed trait SparkContextState
  private case class Running(currentParams: SparkContextParams,
                             currentPromise: Promise[SparkContext],
                             otherPromises: Map[SparkContextParams, Promise[SparkContext]]) extends SparkContextState
  private case object Idle extends SparkContextState
}
