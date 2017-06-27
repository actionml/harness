package com.actionml.core.backup

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
  * Basic trait for JSON back up. Every json sent to POST /engines/engine-id/events will be mirrored by
  * a trait or object extending this.
  *
  */
trait Mirroring extends LazyLogging {

  // globally set in server config
  lazy val config = ConfigFactory.load()

  // Put base dir of ALL mirroring in server/src/main/resources/reference.conf or env, like logs all will go here
  val mirrorContainer: String = if (config.getString("mirror.container").isEmpty) "" else config.getString("mirror.container")
  val mirrorType: String = if (config.getString("mirror.type").isEmpty) "" else config.getString("mirror.type")

  def mirrorJson(engineId: String, json: String): Unit

  /**
    * Collection names are formatted with "yy-MM-dd" template. In a filesystems this is the file name
    * for mirrored files of events. It means differnent things so other types of Mirrors
    *
    * @return timestamp-based name
    */
  protected def batchName: String =
    // yearMonthDay is lexicographically sortable and one file per day seem like a good default.
    DateTimeFormatter.ofPattern("yy-MM-dd").format(LocalDateTime.now(ZoneId.of("UTC")))

  /**
    * Semantics of a Directory name, base dir/engine ID for all the implementations. Override if file/directory
    * semantics do not apply
    *
    * @param engineId Engine ID
    * @return directory name
    */
  protected def containerName(engineId: String): String = s"$mirrorContainer/$engineId"

}

/** TODO: will be used when mirroring is config selectable */
object Mirroring {
  val localfs = "localfs"
  val hdfs = "hdfs"
}
