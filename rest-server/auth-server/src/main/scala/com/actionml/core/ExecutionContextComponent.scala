package com.actionml.core

import scala.concurrent.ExecutionContext

trait ExecutionContextComponent {
  implicit def executionContext: ExecutionContext
}
