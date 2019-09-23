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
import com.actionml.core.jobs.{Cancellable, JobDescription, JobStatuses}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.livy._
import org.apache.livy.scalaapi.{LivyScalaClient, ScalaJobContext, ScalaJobHandle}
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal
import scala.reflect.runtime.universe._

trait SparkJobServerSupport {
  def status(engineId: String): Iterable[JobDescription]
  def cancel(jobId: String): Future[Unit]
}

class SparkJobCancellable(jobId: String) extends Cancellable {
  override def cancel(): Future[Unit] = LivyJobServerSupport.cancel(jobId)
}

object LivyJobServerSupport extends SparkJobServerSupport with LazyLogging {
  private case class ContextId(initParams: String, engineId: String, jobId: String)
  private case class ContextApi(client: LivyScalaClient, handlers: List[ScalaJobHandle[_]])
  private val contexts = TrieMap.empty[ContextId, ContextApi]
  private val config = ConfigFactory.load()
  private val livyUrl = config.getString("spark.job-server-url")

  override def status(engineId: String): Iterable[JobDescription] = {
    contexts.flatMap {
      case (ContextId(_, eId, jobId), api) if eId == engineId =>
        api.handlers.flatMap(handler => state2Status(handler.state).map(s => JobDescription(jobId, s)))
    }
  }

  override def cancel(jobId: String): Future[Unit] = Future.successful ()

  def mkNewClient(initParams: String, engineId: String): (LivyScalaClient, JobDescription) = {
    import scala.collection.JavaConversions._
    val jd = JobDescription.create
    val configMap = configParams ++ parseAndValidate[Map[String, String]](initParams, transform = _ \ "sparkConf")
    val master = configMap.getOrElse("master", "local")
    val conf = new LivyClientBuilder()
      .setURI(new URI(livyUrl))
      .setAll(configMap - "master")
      .setConf("spark.master", master)
      .setConf("spark.app.name", jd.jobId)
      .build
    val client = try {
      val id = ContextId(initParams = initParams, engineId = engineId, jobId = jd.jobId)
      val ContextApi(client, handlers) =
        contexts.getOrElseUpdate(id, ContextApi(new LivyScalaClient(conf), List.empty))
      val lib: Iterable[File] = new File("lib").listFiles
      Await.result(
        Future.sequence {
          lib.filter(f => f.isFile && f.getName.endsWith(".jar"))
            .map(jar => client.addJar(URI.create(s"file://${jar.getAbsolutePath}")))
        } , Duration.Inf)
      contexts.update(id, ContextApi(client, handlers))
      client
    } catch {
      case NonFatal(e) =>
        logger.error("Jars upload problem", e)
        throw e
    }
    (client, jd)
  }


  private def state2Status(state: JobHandle.State): Option[JobStatus] = {
    import JobHandle.State._
    if (state == QUEUED || state == SENT) Some(JobStatuses.queued)
    else if (state == STARTED) Some(JobStatuses.executing)
    else None
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
}
