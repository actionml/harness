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

package com.actionml.core.jobs

import java.util.UUID

import com.actionml.core.jobs.JobStatuses.JobStatus
import com.actionml.core.model.Response
import com.actionml.core.spark.LivyJobServerSupport
import com.typesafe.scalalogging.LazyLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


trait JobManagerInterface {
  def addJob(engineId: String, f: Future[_] = Future.successful(()), c: Cancellable = Cancellable.noop, comment: String = ""): JobDescription
  def getActiveJobDescriptions(engineId: String): Map[String, JobDescription]
  def removeJob(jobId: String): Unit
  def cancelJob(jobId: String): Future[Unit]
}

/** Creates Futures and unique jobDescriptions, both are returned immediately but any arbitrary block of code can be
  * executed in the Future.map. At the end of the block, the consumer must call removeJob. The jobDescriptions are
  * mapped to engineId for status reporting purposes and have UUIDs. If a JobDescription is in the list it is "queued",
  *  or "executing" otherwise it is "notQueued". No information is kept about the completion status of a job so the logs
  * must be scanned for any error reports. Todo: do we want to remember some number of old finished jobs?
  */
object JobManager extends JobManagerInterface with LazyLogging {

  // first key is engineId, second is the harness specific Job id
  private val jobDescriptions: TrieMap[String, Map[String, (Cancellable, JobDescription)]] = TrieMap.empty

  override def addJob(engineId: String, f: Future[_], c: Cancellable, comment: String): JobDescription = {
    val description = JobDescription(createUUID, JobStatuses.executing, comment)
    jobDescriptions.put(engineId,
      jobDescriptions.getOrElse(engineId, Map.empty) +
        (description.jobId -> (c -> description.copy(status = JobStatuses.executing)))
    )
    description
  }

  /** Gets any active Jobs for the specified Engine */
  override def getActiveJobDescriptions(engineId: String): Map[String, JobDescription] = {
    (jobDescriptions.getOrElse(engineId, Map.empty).map {
      case Tuple2(id, Tuple2(_, d)) => d
    } ++ LivyJobServerSupport.status(engineId)).map(d => d.jobId -> d).toMap
  }

  override def removeJob(harnessJobId: String): Unit = {
    jobDescriptions.collectFirst { case (engineId, jds) =>
      jobDescriptions.update(engineId, jds - harnessJobId)
    }
  }

  override def cancelJob(jobId: String): Future[Unit] = {
    Future.sequence(jobDescriptions.collect {
      case (_, jds) if jds.contains(jobId) =>
        jds.get(jobId).map {
          case (cancellable, _) =>
            removeJob(jobId)
            cancellable.cancel()
        }
    }.flatten ++ Seq(LivyJobServerSupport.cancel(jobId)))
      .map(_ => ())
      .recover {
        case NonFatal(e) =>
          logger.error(s"Cancel job error", e)
      }
  }


  private def createUUID: String = UUID.randomUUID().toString
}

trait Cancellable {
  def cancel(): Future[Unit]
}

object Cancellable {
  val noop: Cancellable = new Cancellable {
    override def cancel() = Future.successful(())
  }
}

case class JobDescription(
  jobId: String,
  status: JobStatus = JobStatuses.queued,
  comment: String = ""
) extends Response

object JobDescription {
  def create: JobDescription = JobDescription(UUID.randomUUID().toString)
}

object JobStatuses extends Enumeration {
  type JobStatus = Value
  val queued = Value("queued")
  val executing = Value("executing")
}

