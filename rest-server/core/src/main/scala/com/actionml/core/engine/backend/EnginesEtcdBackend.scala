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

package com.actionml.core.engine.backend

import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.function.{BiConsumer, Consumer}

import com.actionml.core.validate.{ValidRequestExecutionError, ValidateError}
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.watch.WatchResponse
import zio.{IO, Task, ZIO}

import scala.compat.java8.FunctionConverters._
import scala.language.implicitConversions

trait EnginesEtcdBackend[A] extends EnginesBackend[String, A, String] {

  import io.etcd.jetcd.Client

  private val etcdClient: Client = Client.builder.endpoints("http://localhost:2379").build
  private val kv = etcdClient.getKVClient
  private val watch = etcdClient.getWatchClient
  private val prefix = "harness_meta_store.engines."

  protected def encode: A => String
  protected def decode: String => Option[A]

  private implicit def toByteSequence(s: String): ByteSequence = ByteSequence.from(s.getBytes)
  private implicit def toIO[T](f: CompletableFuture[T]): Task[T] = {
    val p = scala.concurrent.Promise[T]()
    f.whenCompleteAsync(new BiConsumer[T, Throwable] {
      override def accept(t: T, u: Throwable): Unit = {
        if (u == null) p.success(t)
        else p.failure(u)
      }
    })
    ZIO.fromFuture(_ => p.future)
  }

  override def addEngine(id: String, data: A): IO[ValidateError, Unit] = {
    import scala.compat.java8.FunctionConverters._
    IO.effectAsync { cb =>
      kv.put(prefix + id, encode(data))
        .handle[Unit](asJavaBiFunction((_, err) => {
          err match {
            case null => cb.apply(IO.unit)
            case e => cb.apply(IO.fail(ValidRequestExecutionError()))
          }
        }))
    }
  }

  override def updateEngine(id: String, data: A): IO[ValidateError, Unit] = addEngine(prefix + id, data)

  override def deleteEngine(id: String): IO[ValidateError, Unit] = {
    kv.delete(prefix + id).thenApply[Unit](asJavaFunction(_ => ()))
  }

  override def findEngine(id: String): IO[ValidateError, A] = {
    import scala.collection.JavaConverters._
    kv.get(id).thenApply[A](asJavaFunction(r => decode(r.getKvs.asScala.head.getKey.toString).get))
  }

  override def listEngines: IO[ValidateError, Iterable[A]] = {
    import scala.collection.JavaConverters._
    kv.get(prefix).thenApply[Iterable[A]](asJavaFunction(r => r.getKvs.asScala.flatMap(v => decode(v.getKey.toString(Charset.forName("UTF-8"))))))
  }

  override def onChange(callback: () => Unit): Unit = {
    watch.watch(prefix, new Consumer[WatchResponse] {
      override def accept(t: WatchResponse): Unit = callback()
    })
  }

  implicit def cmpUnitFuture2io[T](f: CompletableFuture[T]): IO[ValidateError, T] = {
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
