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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.jobs._
import com.actionml.core.validate.{JsonSupport, ValidateError, WrongParams}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.scheduler.{SparkListener, SparkListenerApplicationEnd}
import org.apache.spark.{SparkConf, SparkContext}
import org.json4s._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal


object SparkContextSupport extends LazyLogging with JsonSupport {

  private val state: AtomicReference[SparkContextState] = new AtomicReference(Idle)

  /*
  Creates one context per jvm and one parameters set and gives promises for future contexts.
   */
  def withSparkContext(config: String, engineId: String, kryoClasses: Array[Class[_]] = Array.empty)
                      (fn: SparkContext => Future[Any]): Validated[ValidateError, JobDescription] = synchronized {
    val jobDescription = JobManager.addJob(engineId, comment = "Spark job")
    val params = SparkContextParams(config, engineId, jobDescription, kryoClasses = kryoClasses)
    state.get match {
      case Idle =>
        state.set(Running)
        createSparkContext(params).map { sc =>
          JobManager.updateJob(engineId, jobDescription.jobId, JobStatuses.executing, new SparkCancellable(params.jobDescription.jobId, Future.successful(sc)))
          fn(sc)
            .onComplete {
              case Success(_) =>
                logger.info(s"Job ${jobDescription.jobId} completed successfully")
                JobManager.finishJob(jobDescription.jobId)
                sc.stop
                state.set(Idle)
              case Failure(e) =>
                logger.error(s"Job ${jobDescription.jobId} error", e)
                JobManager.markJobFailed(jobDescription.jobId)
                sc.stop
                state.set(Idle)
            }
          jobDescription
        }
      case Running =>
        Invalid(WrongParams("Training unavailable. Try again later."))
    }
  }


  private def createSparkContext(params: SparkContextParams): Validated[ValidateError, SparkContext] = {
    try {
      val configMap = configParams ++ parseAndValidate[Map[String, String]](params.config, transform = _ \ "sparkConf").getOrElse(Map.empty)
      val conf = new SparkConf()
      configMap.get("master").foreach(conf.setMaster)
      conf.setAppName(s"${params.engineId}:${params.jobDescription.jobId}")
      conf.setAll(configMap - "master")
      val jars = listJars(sys.env.getOrElse("HARNESS_HOME", ".") + s"${File.separator}lib")
      conf.setJars(jars)
      // todo: not sure we should make these keys special, if we do then we should report and error if things like
      // master or appname are required but not provided. We need a better way to report engine.json errors,
      // especially in the sparkConf, which is only partially known. We can check for required things and let the
      // rest through on the hope they are correct
      if (params.kryoClasses.nonEmpty) conf.registerKryoClasses(params.kryoClasses)
      val sc = new SparkContext(conf)
      sc.setJobGroup(params.jobDescription.jobId, s"${params.jobDescription.comment}. EngineId: ${params.engineId}", interruptOnCancel = true)
      sc.addSparkListener(new JobManagerListener(JobManager, params.engineId, params.jobDescription.jobId))
      Valid(sc)
    } catch {
      case NonFatal(e) =>
        logger.error(s"Spark context can not be created for job ${params.jobDescription}", e)
        JobManager.markJobFailed(params.jobDescription.jobId)
        Invalid(WrongParams("Incorrect spark config"))
    }
  }

  private class JobManagerListener(jobManager: JobManagerInterface, engineId: String, jobId: String) extends SparkListener {
    override def onApplicationEnd(applicationEnd: SparkListenerApplicationEnd): Unit = {
      logger.info(s"Job $jobId completed in ${applicationEnd.time} ms [engine $engineId]")
      jobManager.finishJob(jobId)
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
    if (dir.isDirectory) {
      dir.listFiles.collect {
        case f if f.isFile && f.getName.endsWith(".jar") =>
          f.getAbsolutePath
      }
    } else Seq.empty
  }

  private case class SparkContextParams(config: String, engineId: String, jobDescription: JobDescription, kryoClasses: Array[Class[_]])

  private sealed trait SparkContextState
  private case object Running extends SparkContextState
  private case object Idle extends SparkContextState

  class SparkCancellable(jobId: String, f: Future[SparkContext]) extends Cancellable {
    override def cancel(): Future[Unit] = {
      logger.info(s"Cancel job $jobId")
      f.map(_.cancelJobGroup(jobId))
    }
  }
}
