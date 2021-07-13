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

package com.actionml.core.utils

import java.util.concurrent.CompletableFuture

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.HIO
import com.actionml.core.model.Response
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import com.typesafe.scalalogging.LazyLogging
import zio.{IO, Task, ZIO}

object ZIOUtil extends LazyLogging {

  object ValidatedImplicits {
    implicit def validated2IO(v: => Validated[ValidateError, Response]): HIO[Response] = {
      mapError(IO.effect {
        v match {
          case Valid(resp) => IO.succeed(resp)
          case Invalid(e) => ZIO.fail(e)
        }
      }).flatten
    }
  }
  object HioImplicits {
    implicit class CompletableFuture2IO[T](f: CompletableFuture[T]) {
      def toIO: HIO[T] = completableFuture2HIO(f)
      def toTask: Task[T] = ZIO.fromCompletionStage(f)
    }
    implicit def completableFuture2HIO[T](f: CompletableFuture[T]): HIO[T] =
      mapError(ZIO.fromCompletionStage(f))
  }
  object ZioImplicits {
    implicit def completableFuture2Task[T](f: CompletableFuture[T]): Task[T] = ZIO.fromCompletionStage(f)
  }


  private def mapError[A]: Task[A] => HIO[A] = _.mapError { e =>
    logger.error(e.getMessage, e)
    ValidRequestExecutionError(e.getMessage)
  }
}
