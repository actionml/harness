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


/** Creates Futures and unique jobDescriptions, both are returned immediately but any arbitrary block of code can be
  * executed in the Future.map. At the end of the block, the consumer must call removeJob. The jobDescriptions are
  * mapped to engineId for status reporting purposes and have UUIDs. If a JobDescription is in the list it is "queued",
  *  or "executing" otherwise it is "notQueued". No information is kept about the completion status of a job so the logs
  * must be scanned for any error reports. Todo: do we want to remember some number of old finished jobs?
  */
object JobManager {

  // first key is engineId, second is the harness specific Job id
  private var jobDescriptions: Map[String, Map[String, JobDescription]] = Map.empty

  /** Index by the engineId for Engine status reporting purposes */
  def addJob(engineId: String, extId: Option[String] = None, cmnt: String = ""): (JobDescription, Future[Unit]) = {
    val jobId = createUUID
    val newJobDescription = JobDescription(extId, status = JobStatus.queued, comment = cmnt)
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map[String, JobDescription]()) + (jobId -> newJobDescription)
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    (newJobDescription, Future[Unit]{})
  }

  def startJob(engineId: String, jobId: String): Unit = {
    val startedJob = jobDescriptions.getOrElse(engineId, Map.empty).getOrElse(jobId, JobDescription(status = JobStatus.queued))
    val newJobDescriptions = jobDescriptions.getOrElse(engineId, Map.empty) +
      (jobId -> startedJob.copy(status = JobStatus.executing))
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
  }

  private def createUUID: String = UUID.randomUUID().toString

  /** Gets any active Jobs for the specified Engine */
  def getActiveJobDescriptions(engineId: String): Map[String, JobDescription] = {
    if(jobDescriptions.isDefinedAt(engineId)) {
      jobDescriptions(engineId)
    } else Map.empty
  }

  def removeJob(harnessJobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { case (engineId, jds) =>
      engineId -> (jds - harnessJobId)
    }
  }

}

case class JobDescription(
  externalId: Option[String] = None,
  status: String = JobStatus.queued,
  comment: String = "")

object JobStatus {
  val queued = "queued"
  val executing = "executing"
}

