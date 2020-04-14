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
import com.actionml.core.model.Response
import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import zio.{IO, ZIO}

import scala.compat.java8.FunctionConverters.asJavaBiFunction

object ZIOUtil {

  object ImplicitConversions {

    object ValidatedImplicits {
      implicit def validated2IO(v: => Validated[ValidateError, Response]): IO[ValidateError, Response] = {
        IO.effect {
          v match {
            case Valid(resp) => IO.succeed(resp)
            case Invalid(e) => ZIO.fail(e)
          }
        }.mapError(e => ValidRequestExecutionError(e.getMessage)).flatten
      }
    }

    object ZioImplicits {

      implicit class CompletableFuture2IO[T](f: CompletableFuture[T]) {
        def toIO = completableFuture2IO(f)
      }

      implicit def completableFuture2IO[T](f: CompletableFuture[T]): IO[ValidateError, T] =
        IO.effectAsync { cb =>
          f.handle[Unit](asJavaBiFunction((r, err) => {
            err match {
              case null => cb.apply(IO.succeed(r))
              case e => cb.apply(IO.fail(ValidRequestExecutionError()))
            }
          }))
        }
    }

  }
}
