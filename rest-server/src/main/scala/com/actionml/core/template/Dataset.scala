package com.actionml.core.template

import com.actionml.core.storage.Store

abstract class Dataset[T](r: String, s: Store) {

  val resourceId = r
  val store = s

  // todo: we may want to use some operators overloading if this fits some Scala idiom well
  // takes one datum, possibley an Event
  def append(datum: T): Boolean
  // takes a collection of data to append to the Dataset
  def appendAll(data: Seq[T]): Seq[Boolean]

}
