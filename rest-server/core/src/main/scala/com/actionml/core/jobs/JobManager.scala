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

  private var jobDescriptions: Map[String, Seq[JobDescription]] = Map.empty

  /** Pass in the engineId for Engine status reporting purposes */
  def createJob(engineId: String, extId: Option[String] = None, cmnt: String = ""): (JobDescription, Future[Unit]) = {
    val jobId = createUUID
    val newJobDescription = JobDescription(jobId, extId, status = JobStatus.queued, comment = cmnt)
    val newJobDescriptions: Seq[JobDescription] = jobDescriptions.getOrElse(engineId, Seq[JobDescription]()) ++: Seq(newJobDescription)
    jobDescriptions = jobDescriptions + (engineId -> newJobDescriptions)
    (newJobDescription, Future[Unit]{})
  }

  private def createUUID: String = UUID.randomUUID().toString

  /** Gets any active Jobs for the specified Engine */
  def getActiveJobDescriptions(engineId: String): Seq[JobDescription] = {
    if(jobDescriptions.isDefinedAt(engineId)) {
      jobDescriptions(engineId)
    } else Seq.empty[JobDescription]
  }

  def removeJob(harnessJobId: String): Unit = {
    jobDescriptions = jobDescriptions.map { jds =>
      (jds._1, jds._2.filter(_.harnessId != harnessJobId))
    }
  }

}

case class JobDescription(harnessId: String, externalId: Option[String] = None, status: String, comment: String)

object JobStatus {
  val queued = "queued"
  val notQueued = "not queued"
  val executing = "executing"
}

/* Usage example
val (jobId, future) = createJob(engineId, )
future.map{ jobId =>
  some code ...
  removeJob(jobId)
}
Valid(s"jobId")
*/
