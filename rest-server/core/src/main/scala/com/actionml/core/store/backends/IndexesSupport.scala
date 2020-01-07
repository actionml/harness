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
import com.actionml.core.store.indexes.annotations.{CompoundIndex, Index, SingleIndex}
import com.typesafe.scalalogging.LazyLogging
import org.mongodb.scala.MongoCollection

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


trait IndexesSupport {
  this: LazyLogging =>

  def getRequiredIndexesInfo[T: TypeTag](implicit ct: ClassTag[T]): List[(String, Index)] = {
    val symbol = symbolOf[T]
    val singleIndexes = if (symbol.isClass) {
      symbol.asClass.primaryConstructor.typeSignature.paramLists.head
        .collect {
          case f if f.annotations.nonEmpty && f.annotations.forall(_.tree.tpe =:= typeOf[SingleIndex]) =>
            val i = f.annotations.head.tree.children.tail.foldLeft(SingleIndex(Ordering.default, isTtl = false)) {
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

    val compoundIndexes = symbol.annotations.collect {
      case a if a.tree.tpe =:= typeOf[CompoundIndex] =>
        val fields = a.tree.children.collect {
          case Apply(_, args) =>
            args.collect {
              case Apply(fun: TypeApply, args) =>
                val name = fun match {
                  case TypeApply(f: Select, _) =>
                    f.qualifier match {
                      case Apply(_, args) =>
                        args.head match {
                          case c@Constant(_) =>
                            c.value.toString
                          case Literal(Constant(name)) =>
                            name.toString
                        }
                    }
                }
                val value = args.headOption.map {
                  case s: Select =>
                    Ordering.withName(s.name.toString)
                }.getOrElse(Ordering.default)
                name -> value
            }
        }.flatten
        "" -> CompoundIndex(fields)
    }

    singleIndexes ++ compoundIndexes
  }

  // get information about indexes from mongo db
  def getActualIndexesInfo[T](col: MongoCollection[T]): Future[Seq[(String, Index, Option[Duration])]] = {
    import scala.collection.JavaConversions._
    def int2Ordering(i: Int): Ordering.Ordering = i match {
      case 1 => Ordering.asc
      case -1 => Ordering.desc
      case something =>
        logger.warn(s"Got ordering $something instead of '1' or '-1'")
        Ordering.default
    }
    col.listIndexes().map { i =>
      val keyInfo = i.get("key").get.asDocument()
      val indexes = asScalaIterator(keyInfo.entrySet().map(e => (e.getKey, e.getValue.asNumber().intValue())).iterator()).toList
      indexes.foldLeft(Option.empty[CompoundIndex]) {
        case (acc, (name, ordering)) if indexes.size > 1 =>
          val o = int2Ordering(ordering)
          acc.fold(Some(CompoundIndex(List(name -> o)))) { index =>
            Some(CompoundIndex((name -> o) :: index.fields))
          }
        case _ => None
      }.map(i => ("", i, None)).getOrElse {
        val iName = keyInfo.getFirstKey
        val ttlInfo = i.get("expireAfterSeconds")
        val ttlValue = ttlInfo.map { ttl =>
          Duration(ttl.asNumber().intValue(), SECONDS)
        }
        val orderingValue: Ordering.Ordering = int2Ordering(keyInfo.getNumber(iName).intValue())
        (iName, SingleIndex(order = orderingValue, isTtl = ttlInfo.isDefined), ttlValue)
      }
    }.toFuture
  }
}

object IndexesSupport {
  case class IndexInfo(name: String)
}
