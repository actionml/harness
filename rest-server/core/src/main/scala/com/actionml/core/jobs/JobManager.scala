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
import com.actionml.core.store.{DAO, DaoQuery}
import com.actionml.core.store.backends.MongoStorage
import com.typesafe.scalalogging.LazyLogging
import org.bson.{BsonInvalidOperationException, BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal


trait JobManagerInterface {
  def addJob(engineId: String, f: Future[_] = Future.successful(()), c: Cancellable = Cancellable.noop, comment: String = ""): JobDescription
  def getActiveJobDescriptions(engineId: String): Iterable[JobDescription]
  def removeJob(jobId: String): Unit
  def cancelJob(engineId: String, jobId: String): Future[Unit]
  def abortExecuting: Future[Unit]
}

/** Creates Futures and unique jobDescriptions, both are returned immediately but any arbitrary block of code can be
  * executed in the Future.map. At the end of the block, the consumer must call removeJob. The jobDescriptions are
  * mapped to engineId for status reporting purposes and have UUIDs. If a JobDescription is in the list it is "queued",
  *  or "executing" otherwise it is "notQueued". No information is kept about the completion status of a job so the logs
  * must be scanned for any error reports. Todo: do we want to remember some number of old finished jobs?
  */
object JobManager extends JobManagerInterface with LazyLogging {
  import com.actionml.core.store.DaoQuery.syntax._
  private val jobsStore: DAO[JobRecord] = MongoStorage.getStorage("harness_meta_store", codecs = JobRecord.mongoCodecs).createDao[JobRecord]("jobs")
  // first key is engineId, second is the harness specific Job id
  private val jobDescriptions = TrieMap.empty[String, List[(Cancellable, JobDescription)]]

  override def addJob(engineId: String, f: Future[_], c: Cancellable, comment: String): JobDescription = {
    val description = JobDescription.create.copy(status = JobStatuses.executing, comment = comment)
    jobsStore.insert(JobRecord(engineId, description))
    jobDescriptions.put(engineId,
      (c -> description.copy(status = JobStatuses.executing)) :: jobDescriptions.getOrElse(engineId, Nil)
    )
    description
  }

  /** Gets any active Jobs for the specified Engine */
  override def getActiveJobDescriptions(engineId: String): Iterable[JobDescription] = {
    jobsStore
      .findMany("engineId" === engineId)
      .foldLeft(List.empty[JobDescription]) {
        case (acc, j) if j.status == JobStatuses.queued || j.status == JobStatuses.executing =>
          j.toJobDescription :: acc
        case (acc, j) if acc.count(d => d.status != JobStatuses.queued && d.status != JobStatuses.executing) < 10 =>
          j.toJobDescription :: acc
        case (acc, _) => acc
      }
  }

  override def removeJob(harnessJobId: String): Unit = {
    jobsStore.removeOne(("jobId" === harnessJobId))
    jobDescriptions.collectFirst { case (engineId, jds) =>
      jobDescriptions.update(engineId, jds.filterNot(_._2.jobId == harnessJobId))
    }
  }

  override def cancelJob(engineId: String, jobId: String): Future[Unit] = {
    jobDescriptions.get(engineId).fold(Future.successful()) { jds =>
      jds.find(_._2.jobId == jobId).fold(Future.successful()) {
        case (cancellable, description) =>
          val f = cancellable.cancel()
          f.onComplete(_ => removeJob(jobId))
          f.flatMap(_ => LivyJobServerSupport.cancel(jobId))
          f.recover {
            case NonFatal(e) =>
              logger.error(s"Cancel job error", e)
          }
      }
    }
  }

  override def abortExecuting: Future[Unit] = {
    jobsStore
      .updateAsync("status" !== JobStatuses.failed, "status" !== JobStatuses.successful, "status" !== JobStatuses.cancelled)("status" -> JobStatuses.failed)
      .map(_ => ())
  }
}

final case class JobRecord(engineId: String, jobId: String, status: JobStatuses.JobStatus, comment: String) {
  def toJobDescription = JobDescription(jobId, status, comment)
}
object JobRecord {
  def apply(engineId: String, jd: JobDescription): JobRecord = JobRecord(engineId, jd.jobId, jd.status, jd.comment)
  val mongoCodecs: List[CodecProvider] = {
    import org.mongodb.scala.bson.codecs.Macros._
    object JobStatusesEnumCodecProvider extends CodecProvider {
      def isCaseObjectEnum[T](clazz: Class[T]): Boolean = {
        clazz.isInstance(JobStatuses.queued) ||
          clazz.isInstance(JobStatuses.executing) ||
          clazz.isInstance(JobStatuses.failed) ||
          clazz.isInstance(JobStatuses.successful) ||
          clazz.isInstance(JobStatuses.cancelled)
      }

      override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] = {
        if (isCaseObjectEnum(clazz)) {
          CaseObjectEnumCodec.asInstanceOf[Codec[T]]
        } else {
          null
        }
      }

      object CaseObjectEnumCodec extends Codec[JobStatuses.JobStatus] {
        override def decode(reader: BsonReader, decoderContext: DecoderContext): JobStatuses.JobStatus = {
          val enumName = reader.readString()
          Try(JobStatuses.withName(enumName))
            .toOption
            .getOrElse(throw new BsonInvalidOperationException(s"$enumName is an invalid value for a JobStatuses object"))
        }

        override def encode(writer: BsonWriter, value: JobStatuses.JobStatus, encoderContext: EncoderContext): Unit = {
          writer.writeString(value.toString)
        }

        override def getEncoderClass: Class[JobStatuses.JobStatus] = JobStatuses.getClass.asInstanceOf[Class[JobStatuses.JobStatus]]
      }
    }
    List(JobStatusesEnumCodecProvider, classOf[JobRecord])
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
  val successful = Value("successful")
  val failed = Value("failed")
  val cancelled = Value("cancelled")
}

