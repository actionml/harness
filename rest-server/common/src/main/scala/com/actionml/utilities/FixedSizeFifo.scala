package com.actionml.utilities

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

/** immutable Fifo of fixed size so oldest is dropped when the limit is reached */
class FixedSizeFifo[T](val limit: Int)( private val out: List[T], private val in: List[T] )
  extends Traversable[T] with Serializable {

  override def size = in.size + out.size

  def :+( t: T ) = {
    val (nextOut,nextIn) = if (size == limit) {
      if( out.nonEmpty) {
        ( out.tail, t::in )
      } else {
        ( in.reverse.tail, List(t) )
      }
    } else ( out, t::in )
    new FixedSizeFifo( limit )( nextOut, nextIn )
  }

  private lazy val deq = {
    if( out.isEmpty ) {
      val revIn = in.reverse
      ( revIn.head, new FixedSizeFifo( limit )( revIn.tail, List() ) )
    } else {
      ( out.head, new FixedSizeFifo( limit )( out.tail, in ) )
    }
  }
  override lazy val head = deq._1
  override lazy val tail = deq._2

  def foreach[U]( f: T => U ) = ( out ::: in.reverse ) foreach f

}

object FixedSizeFifo {
  def apply[T]( limit: Int ) = new FixedSizeFifo[T]( limit )(List(),List())
}
