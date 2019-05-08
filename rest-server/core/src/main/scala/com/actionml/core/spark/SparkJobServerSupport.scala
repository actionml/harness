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
import java.net.URI

import com.actionml.core.jobs.JobStatuses.JobStatus
import com.actionml.core.jobs.{JobDescription, JobStatuses}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.livy._
import org.apache.spark.SparkContext
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait SparkJobServerSupport {
  def submit[T](initParams: String, engineId: String, jobDescription: JobDescription, sc: SparkContext => T): Unit
  def status(engineId: String): Iterable[JobDescription]
}

object LivyJobServerSupport extends SparkJobServerSupport with LazyLogging {
  private case class ContextId(initParams: String, engineId: String, jobId: String)
  private case class ContextApi(client: LivyClient, handlers: List[JobHandle[_]])
  private val contexts = TrieMap.empty[ContextId, ContextApi]
  private val config = ConfigFactory.load()
  private val livyUrl = config.getString("spark.job-server-url")

  override def submit[T](initParams: String, engineId: String, jobDescription: JobDescription, job: SparkContext => T): Unit = {
    val id = ContextId(initParams = initParams, engineId = engineId, jobId = jobDescription.jobId)
    val ContextApi(client, handlers) =
      contexts.getOrElseUpdate(id, ContextApi(mkNewClient(initParams, engineId, jobDescription.jobId), List.empty))
    val lib: Iterable[File] = new File("lib").listFiles
    try {
      lib.filter(f => f.isFile && f.getName.endsWith(".jar"))
        .foreach(jar => client.uploadJar(jar).get)
      val handler = client.submit(new LivyJob(job))
      contexts.update(id, ContextApi(client, handler :: handlers))
    } catch {
      case e: Exception =>
        logger.error("Jars upload problem", e)
    }
  }

  override def status(engineId: String): Iterable[JobDescription] = {
    contexts.flatMap {
      case (ContextId(_, eId, jobId), api) if eId == engineId =>
        api.handlers.flatMap(handler => state2Status(handler.getState).map(s => JobDescription(jobId, s)))
    }
  }


  private def state2Status(state: JobHandle.State): Option[JobStatus] = {
    if (state == JobHandle.State.QUEUED) Some(JobStatuses.queued)
    else if (state == JobHandle.State.STARTED) Some(JobStatuses.executing)
    else None
  }

  private def mkNewClient(initParams: String, engineId: String, jobId: String): LivyClient = {
    import scala.collection.JavaConversions._
    val configMap = configParams ++ parseAndValidate[Map[String, String]](initParams, transform = _ \ "sparkConf")
    new LivyClientBuilder()
      .setURI(new URI(livyUrl))
      .setAll(configMap)
      .setConf("spark.app.name", jobId)
      .build()
  }

  private lazy val configParams: Map[String, String] = {
    import net.ceedubs.ficus.Ficus._
    Try {
      Map("spark.eventLog.dir" -> config.as[String]("spark.eventLog.dir"))
    }.getOrElse(Map.empty)
  }

  private def parseAndValidate[T](jsonStr: String, transform: JValue => JValue = a => a)(implicit mf: Manifest[T]): T = {
    implicit val _ = org.json4s.DefaultFormats
    transform(parse(jsonStr)).extract[T]
  }

  private class LivyJob[T](job: SparkContext => T) extends Job[T] {
    override def call(jobContext: JobContext): T = job(jobContext.sc())
  }
}
