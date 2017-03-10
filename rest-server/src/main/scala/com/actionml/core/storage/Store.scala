package com.actionml.core.storage

import com.typesafe.scalalogging.LazyLogging

abstract class Store  extends LazyLogging {

  def create(name: String = "test-store"): Store

  def destroy(name: String = "test-store"): Unit

}
