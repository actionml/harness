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
package com.actionml.core

import com.actionml.core.validate.ValidRequestExecutionError
import zio.logging.Logging
import zio.{Cause, Has, IO, Layer, UIO, ZIO, ZLayer, ZManaged}

import java.net.URL
import scala.io.Source
import scala.util.Try


package object utils {
  type HttpClient = Has[HttpClient.Service]

  object HttpClient {
    trait Service {
      def send(url: URL): HIO[String]
    }

    def send(url: URL): HIO[String] = ZIO.accessM(env => env.get[Service].send(url))

    val live: Layer[Throwable, HttpClient] = ZLayer.fromEffect { IO.effect {
      new Service {
        override def send(url: URL): HIO[String] = IO.effect {
          val source = Source.fromURL(url, "UTF-8")
          val result = source.getLines().mkString("\n")
          Try(source.close)
          result
        }.flatMapError { e =>
          Logging.error("Http client error", Cause.die(e))
            .as(ValidRequestExecutionError("HTTP client error"))
        }
      }
    }}
  }
}
