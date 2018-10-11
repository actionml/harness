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

import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}


object SparkContextSupport extends LazyLogging {

  private val state: AtomicReference[SparkContextState] = new AtomicReference(Idle)

  /*
  Stops context and failures all promises
   */
  def reset: Unit = state.get match {
    case Idle =>
    case s@Running(_, _, currentPromise, otherPromises) =>
      if (currentPromise.isCompleted) {
        currentPromise.future.value.foreach {
          case Success(sc) => if (!sc.isStopped) sc.stop()
          case _ =>
        }
      } else currentPromise.failure(new InterruptedException)
      otherPromises.foreach(_._2.failure(new InterruptedException))
      state.compareAndSet(s, Idle)
  }

  def stopAndClean(sc: SparkContext): Unit = {
    state.get match {
      case s@Running(_, optSc, _, promises) if promises.nonEmpty =>
        val (p, others) = (promises.head, promises.tail)
        optSc.foreach(_.stop())
        val newSc = createSparkContext(p._1)
        p._2.complete(newSc)
        state.compareAndSet(s, Running(p._1, newSc.toOption, p._2, others))
      case s@Running(_, optSc, _, _)  =>
        optSc.foreach(_.stop())
        state.compareAndSet(s, Idle)
      case Idle =>
    }
  }

  /*
  Creates one context per jvm and one parameters set and gives promises for future contexts.
   */
  def getSparkContext(config: String, defaults: Map[String, String], kryoClasses: Array[Class[_]] = Array.empty): Future[SparkContext] = {
    val params = SparkContextParams(config, defaults: Map[String, String], kryoClasses)
    state.get match {
      case Idle =>
        val p = Promise[SparkContext]()
        val futureContext = createSparkContext(params)
        if (state.compareAndSet(Idle, Running(params, futureContext.toOption, p, Map.empty))) {
          p.complete(futureContext)
          p.future
        } else getSparkContext(config, defaults, kryoClasses)
      case Running(currentParams, _, p, _) if currentParams == params && p.isCompleted && p.future.value.forall(r => r.isSuccess && !r.get.isStopped) =>
        p.future
      case s@Running(_, sc, p, promises) if !sc.exists(_.isStopped) =>
        promises.getOrElse(params, {
          val p = Promise[SparkContext]()
          state.compareAndSet(s, s.copy(otherPromises = promises + (params -> p)))
          p
        }).future
      case s@Running(_, sc, p, promises) if sc.forall(_.isStopped) =>
        p.tryFailure(new IllegalStateException())
        promises.foreach(_._2.tryFailure(new IllegalStateException()))
        Future.fromTry(createSparkContext(params)).map { sc =>
          state.compareAndSet(s, Running(params, Some(sc), Promise.successful(sc), Map.empty))
          sc
        }
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
    val jars = listJars(sys.env.getOrElse("HARNESS_HOME", ".") + s"${File.separator}lib")
    conf.setJars(jars)
    // todo: not sure we should make these keys special, if we do then we should report and error if things like
    // master or appname are required but not provided. We need a better way to report engine.json errors,
    // especially in the sparkConf, which is only partially known. We can check for required things and let the
    // rest through on the hope they are correct
    if (params.kryoClasses.nonEmpty) conf.registerKryoClasses(params.kryoClasses)
    new SparkContext(conf)
  }

  private def listJars(path: String): Seq[String] = {
    val dir = new File(path)
    if (dir.exists() && dir.isDirectory) {
      dir.listFiles.collect {
        case f if f.isFile && f.getName.endsWith(".jar") =>
          f.getAbsolutePath
      }
    } else Seq.empty
  }

  private def parseAndValidate[T](jsonStr: String, transform: JValue => JValue = a => a)(implicit mf: Manifest[T]): T = {
    implicit val _ = org.json4s.DefaultFormats
    transform(parse(jsonStr)).extract[T]
  }

  private case class SparkContextParams(config: String, defaults: Map[String, String], kryoClasses: Array[Class[_]])

  private sealed trait SparkContextState
  private case class Running(currentParams: SparkContextParams,
                             sparkContext: Option[SparkContext],
                             currentPromise: Promise[SparkContext],
                             otherPromises: Map[SparkContextParams, Promise[SparkContext]]) extends SparkContextState
  private case object Idle extends SparkContextState
}
