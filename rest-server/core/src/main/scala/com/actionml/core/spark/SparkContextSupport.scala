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

import com.actionml.core.jobs.{Cancellable, JobDescription, JobManager, JobManagerInterface}
import com.actionml.core.validate.JsonSupport
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.scheduler.{SparkListener, SparkListenerApplicationEnd}
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}


object SparkContextSupport extends LazyLogging with JsonSupport {

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
        createSparkContext(p._1).foreach { newSc =>
          p._2.complete(Success(newSc))
          state.compareAndSet(s, Running(p._1, Option(newSc), p._2, others))
        }
      case s@Running(_, optSc, _, _)  =>
        optSc.foreach(_.stop())
        state.compareAndSet(s, Idle)
      case Idle =>
    }
  }

  /*
  Creates one context per jvm and one parameters set and gives promises for future contexts.
   */
  def getSparkContext(config: String, engineId: String, kryoClasses: Array[Class[_]] = Array.empty): (Future[SparkContext], JobDescription) = {
    val jobDescription = JobManager.addJob(engineId, comment = "Spark job")
    val params = SparkContextParams(config, engineId, jobDescription, kryoClasses = kryoClasses)
    state.get match {
      case Idle =>
        val p = Promise[SparkContext]()
        createSparkContext(params).foreach { sc =>
          if (state.compareAndSet(Idle, Running(params, Option(sc), p, Map.empty))) {
            p.complete(Success(sc))
          } else {
            state.get match {
              case r: Running => state.compareAndSet(r, r.copy(otherPromises = r.otherPromises + (params -> p)))
              case _ => getSparkContext(config, engineId, kryoClasses)
            }
          }
        }
        val f = p.future
        val desc = JobManager.addJob(engineId, f, new SparkCancellable(params.jobDescription.jobId, f), "Spark job")
        JobManager.removeJob(jobDescription.jobId)
        (f, desc)
      case Running(currentParams, _, p, _) if currentParams == params && p.isCompleted && p.future.value.forall(r => r.isSuccess && !r.get.isStopped) =>
        (p.future, currentParams.jobDescription)
      case s@Running(currentParams, sc, _, promises) if !sc.exists(_.isStopped) =>
        (promises.getOrElse(params, {
          val p = Promise[SparkContext]()
          state.compareAndSet(s, s.copy(otherPromises = promises + (params -> p)))
          p
        }).future, currentParams.jobDescription)
      case s@Running(currentParams, sc, p, promises) if sc.forall(_.isStopped) =>
        p.tryFailure(new IllegalStateException())
        promises.foreach(_._2.tryFailure(new IllegalStateException()))
        (createSparkContext(params).map { sc =>
          state.compareAndSet(s, Running(params, Some(sc), Promise.successful(sc), Map.empty))
          sc
        }, currentParams.jobDescription)
    }
  }


  private def createSparkContext(params: SparkContextParams): Future[SparkContext] = {
    val f = Future {
      val configMap = configParams ++ parseAndValidate[Map[String, String]](params.config, transform = _ \ "sparkConf").getOrElse(Map.empty)
      val conf = new SparkConf()
      configMap.get("master").foreach(conf.setMaster)
      conf.setAppName(params.engineId)
      conf.setAll(configMap - "master")
      val jars = listJars(sys.env.getOrElse("HARNESS_HOME", ".") + s"${File.separator}lib")
      conf.setJars(jars)
      // todo: not sure we should make these keys special, if we do then we should report and error if things like
      // master or appname are required but not provided. We need a better way to report engine.json errors,
      // especially in the sparkConf, which is only partially known. We can check for required things and let the
      // rest through on the hope they are correct
      if (params.kryoClasses.nonEmpty) conf.registerKryoClasses(params.kryoClasses)
      val sc = new SparkContext(conf)
      sc.setJobGroup(params.jobDescription.jobId, params.jobDescription.comment, interruptOnCancel = true)
      sc.addSparkListener(new JobManagerListener(JobManager, params.engineId, params.jobDescription.jobId))
      sc
    }
    f.onComplete {
      case Failure(e) =>
        logger.error(s"Spark context failed for job ${params.jobDescription}", e)
        JobManager.removeJob(params.jobDescription.jobId)
      case _ =>
    }
    f
  }

  private class JobManagerListener(jobManager: JobManagerInterface, engineId: String, jobId: String) extends SparkListener {
    override def onApplicationEnd(applicationEnd: SparkListenerApplicationEnd): Unit = {
      logger.info(s"Job $jobId completed in ${applicationEnd.time} ms [engine $engineId]")
      jobManager.removeJob(jobId)
    }
  }

  private def configParams: Map[String, String] = {
    import com.typesafe.config.ConfigFactory
    import net.ceedubs.ficus.Ficus._
    Try {
      val config: Config = ConfigFactory.load()
      Map("spark.eventLog.dir" -> config.as[String]("spark.eventLog.dir"))
    }.getOrElse(Map.empty)
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

  private case class SparkContextParams(config: String, engineId: String, jobDescription: JobDescription, kryoClasses: Array[Class[_]])

  private sealed trait SparkContextState
  private case class Running(currentParams: SparkContextParams,
                             sparkContext: Option[SparkContext],
                             currentPromise: Promise[SparkContext],
                             otherPromises: Map[SparkContextParams, Promise[SparkContext]]) extends SparkContextState
  private case object Idle extends SparkContextState

  class SparkCancellable(jobId: String, f: Future[SparkContext]) extends Cancellable {
    override def cancel(): Future[Unit] = {
      logger.info(s"Cancel job $jobId")
      f.map(_.cancelJobGroup(jobId))
    }
  }
}
