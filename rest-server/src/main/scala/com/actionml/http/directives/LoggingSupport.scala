package com.actionml.http.directives

import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry, LoggingMagnet}

/**
  *
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 11:58
  */
trait LoggingSupport {

  def akkaResponseTimeLoggingFunction(
    loggingAdapter: LoggingAdapter,
    requestTimestamp: Long,
    level: LogLevel = Logging.InfoLevel
  )(req: HttpRequest)(res: Any): Unit = {
    val entry = res match {
      case Complete(resp) =>
        val responseTimestamp: Long = System.nanoTime
        val elapsedTime: Long = (responseTimestamp - requestTimestamp) / 1000000
        val loggingString =
          s"""
             |Complete: ${req.method}:${req.uri} -> ${resp.status}:${elapsedTime}ms.
             |${req.entity}
             |${resp.entity}""".stripMargin
        LogEntry(loggingString, level)
      case Rejected(reason) =>
        LogEntry(s"Rejected Reason: ${reason.mkString(",")}", level)
    }
    entry.logTo(loggingAdapter)
  }
  def printResponseTime(log: LoggingAdapter): (HttpRequest) => (Any) => Unit = {
    val requestTimestamp = System.nanoTime
    akkaResponseTimeLoggingFunction(log, requestTimestamp)(_)
  }

  val logResponseTime: Directive0 = DebuggingDirectives.logRequestResult(LoggingMagnet(printResponseTime))

}
