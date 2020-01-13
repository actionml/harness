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

import com.actionml.core.jobs.{JobDescription, JobManager}
import com.actionml.core.jobs.JobStatuses._
import com.actionml.core.spark.SparkClient._
import org.apache.spark.SparkContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

trait SparkClient {
  def sparkClient(config: String, useSparkJobServer: Boolean, engineId: String, kryoClasses: Array[Class[_]] = Array.empty): SparkClient.Service =
    if (useSparkJobServer) new ClusterService(config, engineId, kryoClasses)
    else new LocalService(config, engineId, kryoClasses)
}

object SparkClient {
  trait Service {
    def submit[T](fn: SparkContext => T): (Future[T], JobDescription)
  }

  class LocalService(config: String, engineId: String, kryoClasses: Array[Class[_]]) extends Service {
    override def submit[T](fn: SparkContext => T): (Future[T], JobDescription) = {
      val (futureSc, description) = SparkContextSupport.getSparkContext(config, engineId, kryoClasses)
      (futureSc.flatMap { sc =>
        try {
          JobManager.updateJob(engineId, description.jobId, status = executing)
          val result = fn(sc)
          JobManager.finishJob(description.jobId)
          Future.successful(result)
        } catch {
          case NonFatal(e) =>
            JobManager.markJobFailed(description.jobId)
            Future.failed(e)
        }
      }, description)
    }
  }

  class ClusterService(config: String, engineId: String, kryoClasses: Array[Class[_]]) extends Service {
    override def submit[T](fn: SparkContext => T): (Future[T], JobDescription) = {
      val promise = Promise[T]()
      val jobDescription = JobDescription.create(status = queued, comment = "Livy job")
      try {
        val client = LivyJobServerSupport.mkNewClient(config, engineId, jobDescription)
        val handler = client.submit(jc => fn(jc.sc))
        handler.onSuccess { case t =>
          promise.success(t)
          JobManager.finishJob(jobDescription.jobId)
        }
        handler.onFailure { case NonFatal(e) =>
          promise.failure(e)
          JobManager.markJobFailed(jobDescription.jobId)
        }
        handler.onJobCancelled { isCancelled =>
          if (isCancelled) {
            JobManager.cancelJob(engineId, jobDescription.jobId)
            promise.failure(new RuntimeException(s"Job ${jobDescription} for engine $engineId was cancelled"))
          }
        }
        handler.onJobQueued(JobManager.updateJob(engineId, jobDescription.jobId, status = queued))
        handler.onJobStarted(JobManager.updateJob(engineId, jobDescription.jobId, status = executing))
      } catch {
        case NonFatal(e) => promise.failure(e)
      }
      (promise.future, jobDescription)
    }
  }
}
