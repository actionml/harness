package com.actionml.core.storage

import com.typesafe.scalalogging.LazyLogging

trait Store extends LazyLogging {

  def create(): Store

  def destroy(): Store

}
