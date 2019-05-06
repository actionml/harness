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

package com.actionml.core.store.backends


import com.actionml.core.store.Ordering
import com.actionml.core.store.indexes.annotations.Indexed
import org.mongodb.scala.MongoCollection

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


trait IndexesSupport {
  def getRequiredIndexesInfo[T: TypeTag](implicit ct: ClassTag[T]): List[(String, Indexed)] = {
    val symbol = symbolOf[T]
    if (symbol.isClass) {
      symbol.asClass.primaryConstructor.typeSignature.paramLists.head
        .collect {
          case f if f.annotations.nonEmpty && f.annotations.forall(_.tree.tpe =:= typeOf[Indexed]) =>
            val i = f.annotations.head.tree.children.tail.foldLeft(Indexed(Ordering.default, isTtl = false)) {
              case (acc, a@Select(t, n)) if a.tpe =:= typeOf[com.actionml.core.store.Ordering.Value] =>
                util.Try(Ordering.withName(a.name.toString)).toOption.fold(acc)(o => acc.copy(order = o))
              case (acc, Literal(Constant(id: Boolean))) =>
                acc.copy(isTtl = id)
              case (acc, v) =>
                acc
            }
            (f.name.toString, i)
        }
    } else List.empty
  }

  // get information about indexes from mongo db
  def getActualIndexesInfo[T](col: MongoCollection[T]): Future[Seq[(String, Indexed, Option[Duration])]] = {
    col.listIndexes().map { i =>
      val keyInfo = i.get("key").get.asDocument()
      val iName = keyInfo.getFirstKey
      val ttlInfo = i.get("expireAfterSeconds")
      val ttlValue = ttlInfo.map { ttl =>
        Duration(ttl.asInt64().getValue, SECONDS)
      }
      val orderingValue: Ordering.Ordering = i.get("v").map { _.asInt32().getValue match {
        case 1 => Ordering.asc
        case 2 => Ordering.desc
      }}.getOrElse(Ordering.default)
      (iName, Indexed(order = orderingValue, isTtl = ttlInfo.isDefined), ttlValue)
    }.toFuture
  }
}
