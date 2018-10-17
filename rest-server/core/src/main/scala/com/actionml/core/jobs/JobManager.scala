package com.actionml.core.jobs

import java.util.UUID

import scala.collection.immutable.Queue
import scala.util.Try

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

/** Mixin if you need to run a Try. One per instance of the trait. Will report the jobId if the try is running */
object JobManager {

  private var jobIds: Queue[(String, String)] = Queue.empty

  def createJob(engineId: String, jobId: String = createUUID): (String, Try[Unit]) = {
    jobIds = jobIds.enqueue((jobId, engineId))
    (jobId, Try[Unit]{})
  }

  private def createUUID: String = UUID.randomUUID().toString

  def getActiveJobIds(engineId: String): Seq[(String, String)] = {
    jobIds.filter(_._2 == engineId)
  }

  def removeJob(jobId: String): Unit = {
    jobIds = jobIds.filter(_._2 != jobId)
  }

}

/* Usage example
val (jobId, future) = createJob(engineId = someString)
future.map{ jobId =>
  some code ...
  removeJob(jobId)
}
Valid(s"jobId")
*/
