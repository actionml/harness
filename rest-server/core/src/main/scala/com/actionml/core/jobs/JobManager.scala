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
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


trait JobManagerInterface {
  def addJob(engineId: String, cancellable: Cancellable = Cancellable.noop, comment: String = ""): JobDescription
  def startJob(jobId: String): Unit
  def startNewJob(engineId: String, f: Future[_], c: Cancellable = Cancellable.noop, comment: String = ""): JobDescription
  def getActiveJobDescriptions(engineId: String): Map[String, JobDescription]
  def removeJob(harnessJobId: String): Unit
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
  private var jobDescriptions: Map[String, Map[String, (Cancellable, JobDescription)]] = Map.empty

  /** Index by the engineId for Engine status reporting purposes */
  override def addJob(engineId: String, cancellable: Cancellable, cmnt: String = ""): JobDescription = {
    val jobId = createUUID
    val newJobDescription = JobDescription(jobId, status = JobStatuses.queued, comment = cmnt)
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map.empty) + (jobId -> (cancellable -> newJobDescription))
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    newJobDescription
  }

  override def startJob(jobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { case (engineId, jds) =>
      jds.get(jobId).fold(engineId -> jds) { d =>
        engineId -> (jds + (jobId -> (d._1 -> d._2.copy(status = JobStatuses.executing))))
      }
    }
  }

  override def startNewJob(engineId: String, f: Future[_], c: Cancellable, comment: String): JobDescription = {
    val description = JobDescription(createUUID, JobStatuses.executing, comment)
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map.empty) +
      (description.jobId -> (c -> description.copy(status = JobStatuses.executing)))
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    f.onComplete {
      case Success(_) =>
        logger.info(s"Job ${description.jobId} completed successfully [engine $engineId]")
        removeJob(description.jobId)
      case Failure(e) =>
        logger.error(s"Job $description failed [engine $engineId]", e)
        removeJob(description.jobId)
    }
    description
  }

  private def createUUID: String = UUID.randomUUID().toString

  /** Gets any active Jobs for the specified Engine */
  override def getActiveJobDescriptions(engineId: String): Map[String, JobDescription] = {
    jobDescriptions.getOrElse(engineId, Map.empty).map {
      case Tuple2(id, Tuple2(c, d)) => id -> d
    }
  }

  override def removeJob(harnessJobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { case (engineId, jds) =>
      engineId -> (jds - harnessJobId)
    }
  }

  override def cancelJob(jobId: String): Future[Unit] = {
    jobDescriptions.foreach {
      case (_, jds) if jds.contains(jobId) =>
        jds.get(jobId).foreach {
          case (cancellable, _) =>
            removeJob(jobId)
            cancellable.cancel()
        }
    }
    Future.successful(())
  }

}


trait Cancellable {
  def cancel(): Future[Unit]
}
object Cancellable {
  val noop: Cancellable = new Cancellable {
    override def cancel() = Future.successful(())
  }
}

case class JobDescription(jobId: String,
                          status: JobStatus = JobStatuses.queued,
                          comment: String = "") extends Response

object JobStatuses extends Enumeration {
  type JobStatus = Value
  val queued = Value("queued")
  val executing = Value("executing")
}

