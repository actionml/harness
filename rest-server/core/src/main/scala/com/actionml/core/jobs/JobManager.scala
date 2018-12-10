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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait JobManagerInterface {
  def addJob(engineId: String, comment: String = ""): JobDescription
  def startJob(jobId: String): Unit
  def startNewJob(engineId: String, f: Future[_], comment: String = ""): JobDescription
  def getActiveJobDescriptions(engineId: String): Map[String, JobDescription]
  def removeJob(harnessJobId: String): Unit
}

/** Creates Futures and unique jobDescriptions, both are returned immediately but any arbitrary block of code can be
  * executed in the Future.map. At the end of the block, the consumer must call removeJob. The jobDescriptions are
  * mapped to engineId for status reporting purposes and have UUIDs. If a JobDescription is in the list it is "queued",
  *  or "executing" otherwise it is "notQueued". No information is kept about the completion status of a job so the logs
  * must be scanned for any error reports. Todo: do we want to remember some number of old finished jobs?
  */
object JobManager extends JobManagerInterface {

  // first key is engineId, second is the harness specific Job id
  private var jobDescriptions: Map[String, Map[String, JobDescription]] = Map.empty

  /** Index by the engineId for Engine status reporting purposes */
  override def addJob(engineId: String, cmnt: String = ""): JobDescription = {
    val jobId = createUUID
    val newJobDescription = JobDescription(jobId, status = JobStatus.queued, comment = cmnt)
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map.empty) + (jobId -> newJobDescription)
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    newJobDescription
  }

  override def startJob(jobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { case (engineId, jds) =>
      jds.get(jobId).fold(engineId -> jds) { d =>
        engineId -> (jds + (jobId -> d.copy(status = JobStatus.executing)))
      }
    }
  }

  override def startNewJob(engineId: String, f: Future[_], comment: String): JobDescription = {
    val description = JobDescription(createUUID, JobStatus.executing, comment)
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map.empty) +
      (description.jobId -> description.copy(status = JobStatus.executing))
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    f.map(_ => removeJob(description.jobId))
     .recover { case _ => removeJob(description.jobId) }
    description
  }

  private def createUUID: String = UUID.randomUUID().toString

  /** Gets any active Jobs for the specified Engine */
  override def getActiveJobDescriptions(engineId: String): Map[String, JobDescription] = {
    jobDescriptions.getOrElse(engineId, Map.empty)
  }

  override def removeJob(harnessJobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { case (engineId, jds) =>
      engineId -> (jds - harnessJobId)
    }
  }

}

case class JobDescription(
  jobId: String,
  status: String = JobStatus.queued,
  comment: String = "")

object JobStatus {
  val queued = "queued"
  val executing = "executing"
}

