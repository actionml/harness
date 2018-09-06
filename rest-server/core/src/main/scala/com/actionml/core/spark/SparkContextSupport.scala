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
import java.util.concurrent.atomic.AtomicReference

import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.validate.ValidRequestExecutionError
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.Try


object SparkContextSupport {

  private val state: AtomicReference[SparkContextState] = new AtomicReference(Idle)

  def getSparkContext(config: String, defaults: Map[String, String]): Future[SparkContext] = {
    val params = SparkContextParams(config, defaults: Map[String, String])
    state.get match {
      case Idle =>
        val p = Promise[SparkContext]()
        if (state.compareAndSet(Idle, Running(params, p, Map.empty))) {
          p.completeWith(Future.fromTry(createSparkContext(params)))
          p.future
        } else getSparkContext(config, defaults)
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
    // todo: not sure we should make these keys special, if we do then we should report and error if things like
    // master or appname are required but not provided. We need a better way to report engine.json errors,
    // especially in the sparkConf, which is only partially known. We can check for required things and let the
    // rest through on the hope they are correct
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
