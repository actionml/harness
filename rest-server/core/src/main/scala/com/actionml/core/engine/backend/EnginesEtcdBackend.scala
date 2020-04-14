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
import java.util.function.Consumer

import com.actionml.core.config.EtcdConfig
import com.actionml.core.validate.{ResourceNotFound, ValidRequestExecutionError, ValidateError}
import io.etcd.jetcd.options.{GetOption, WatchOption}
import io.etcd.jetcd.watch.WatchResponse
import io.etcd.jetcd.{ByteSequence, Client}
import zio.{IO, ZIO, ZLayer}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

trait EnginesEtcdBackend[A] extends EnginesBackend[String, A, String] {
  protected def config: EtcdConfig

  private val etcdClient: IO[ValidateError, Client] =
    IO.effect(Client.builder.endpoints(config.endpoints: _*).build)
      .mapError(e => ValidRequestExecutionError())
  private val kv = etcdClient.map(_.getKVClient)
  private val watch = etcdClient.map(_.getWatchClient)
  private val prefix = "/harness_meta_store/engines/"
  private val etcdCharset = Charset.forName("UTF-8")
  private implicit def toByteSequence(s: String): ByteSequence = ByteSequence.from(s.getBytes)
  import com.actionml.core.utils.ZIOUtil.ImplicitConversions.ZioImplicits._

  protected def encode: A => String
  protected def decode: String => IO[ValidateError, A]

  override def addEngine(id: String, data: A): IO[ValidateError, Unit] = {
    for {
      client <- kv
      _ <- client.put(prefix + id, encode(data)).unit
    } yield ()
  }

  override def updateEngine(id: String, data: A): IO[ValidateError, Unit] = {
    for {
      client <- kv
      _ <- client.put(prefix + id, encode(data)).unit
    } yield ()
  }

  override def deleteEngine(id: String): IO[ValidateError, Unit] = {
    for {
      client <- kv
      _ <- client.delete(prefix + id).unit
    } yield ()
  }

  override def findEngine(id: String): IO[ValidateError, A] = {
    for {
      client <- kv
      r <- client.get(id)
      e <- r.getKvs.asScala.headOption.fold[IO[ValidateError, A]](IO.fail(ResourceNotFound(s"Engine $id not found"))) { e =>
        decode(e.getKey.toString(etcdCharset))
      }
    } yield e
  }

  override def listEngines: IO[ValidateError, Iterable[A]] = {
    val opts = GetOption.newBuilder().withPrefix(prefix).build()
    for {
      client <- kv
      response <- client.get(prefix, opts)
      result <- ZIO.collectAll(response.getKvs.asScala.map { v =>
        decode(v.getValue.toString(etcdCharset))
      })
    } yield result
  }

  override def onChange(callback: () => Unit): Unit = {
    val watchOptions = WatchOption.newBuilder().withPrefix(prefix).build
    zio.Runtime.unsafeFromLayer(ZLayer.succeed()).unsafeRunSync {
      watch.map(_.watch(ByteSequence.EMPTY, watchOptions, new Consumer[WatchResponse] {
        override def accept(t: WatchResponse): Unit = callback()
      }))
    }
    callback()
  }
}
