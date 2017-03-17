package com.actionml.core.storage

import com.typesafe.scalalogging.LazyLogging

abstract class Store  extends LazyLogging {

  def create(): Store

  def destroy(): Store

}
