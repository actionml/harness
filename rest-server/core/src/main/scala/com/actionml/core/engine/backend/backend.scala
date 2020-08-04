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

package com.actionml.core.engine

import com.actionml.core.validate.{ExternalServiceError, ValidateError}
import io.etcd.jetcd.{Client, KV, Lease, Watch}
import zio._

package object backend {
  type EtcdSupport = Has[EtcdSupport.Service]

  object EtcdSupport {

    trait Service {
      def getKV: Task[KV]
      def getLease: Task[Lease]
      def getWatch: Task[Watch]
    }

    def getEtcd(endpoints: Seq[String]): Layer[Any, EtcdSupport] = ZLayer.succeed {
      new Service {
        private val client: Task[Client] = IO.effect(Client.builder.endpoints(endpoints: _*).build)
        override def getKV = client.map(_.getKVClient)
        override def getLease = client.map(_.getLeaseClient)
        override def getWatch = client.map(_.getWatchClient)
      }
    }

    def getKV: ZIO[EtcdSupport,ValidateError,KV] = (ZIO.accessM(_.get.getKV): ZIO[EtcdSupport,Throwable,KV])
      .orElseFail(ExternalServiceError("Etcd error"))
    def getLease: ZIO[EtcdSupport,ValidateError,Lease] = (ZIO.accessM(a => a.get.getLease): ZIO[EtcdSupport, Throwable,Lease])
      .orElseFail(ExternalServiceError("Etcd error"))
    def getWatch: ZIO[EtcdSupport,Throwable,Watch] = ZIO.accessM(_.get.getWatch)
  }
}