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

import java.util.Date
import JobStatuses._

import org.scalatest.{FlatSpec, Matchers}

class JobRecordSpec extends FlatSpec with Matchers {
  import JobRecord.defaultExpireMillis
  def jobRecord(createdAt: Date) = JobRecord("", JobDescription("", createdAt = Option(createdAt), node = ""))
  def now(delta: Long = 0): Date = new Date(System.currentTimeMillis() + delta)

  "expiredStatus" should "be set only for expired 'queued' or 'executing' statuses" in {
    jobRecord(now(-defaultExpireMillis - 1000))
      .toJobDescription
      .status shouldEqual expired

    jobRecord(now(-defaultExpireMillis + 1000))
      .toJobDescription
      .status shouldEqual JobDescription.createSync.status
  }

  it should "not be set for failed or successful statuses" in {
    jobRecord(now(-defaultExpireMillis - 1000))
      .copy(status = successful)
      .toJobDescription
      .status shouldEqual successful

    jobRecord(now(-defaultExpireMillis - 1000))
      .copy(status = failed)
      .toJobDescription
      .status shouldEqual failed
  }
}
