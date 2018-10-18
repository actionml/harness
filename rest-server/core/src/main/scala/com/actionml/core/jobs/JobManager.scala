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


/** Creates Try type Futures and unique jobIds, both are returned immediately but any arbitrary block of code can be
  * executed in the Try.map. At the end of the block, the consumer must call removeJob. The jobIds have a UUID as a String
  * and the engineId for status reporting purposes. If a jobId is in the list it is "pending", otherwise it is "complete"
  */
object JobManager {

  private var jobIds: Seq[(String, String)] = Seq.empty

  /** Pass in the engineId for Engine status reporting purposes */
  def createJob(engineId: String): (String, Future[Unit]) = {
    val jobId = createUUID
    jobIds = jobIds :+ (jobId, engineId)
    (jobId, Future[Unit]{})
  }

  private def createUUID: String = UUID.randomUUID().toString

  /** Gets any active Jobs for the specified Engine */
  def getActiveJobIds(engineId: String): Seq[(String, String)] = {
    jobIds.filter(_._2 == engineId)
  }

  def removeJob(jobId: String): Unit = {
    jobIds = jobIds.filter(_._1 != jobId)
  }

}

/* Usage example
val (jobId, future) = createJob(engineId)
future.map{ jobId =>
  some code ...
  removeJob(jobId)
}
Valid(s"jobId")
*/
