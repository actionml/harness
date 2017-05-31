package com.actionml.core.backup

import java.io.{File, PrintWriter}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import scala.util.Try

/**
  *
  * That's a very basic implementation of JSON request backing up.
  * The trait could be mixed in any class which needs the mirroring.
  *
  */
trait Mirroring {
  private val dirNamePattern = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
  private val fileNamePattern = DateTimeFormatter.ofPattern("HH-mm-ss.SSS")

  /**
    *
    * The method mirrors the input JSON to a file named considering <i>HH-mm-ss.SSS.json</i> pattern
    * It's placed to a folder with a name like <i>dd-MMM-yyyy</i>. So there is one folder per day.
    * In case of the file already exists in the directory it's supposed to be overwritten
    * (i.e. the maximal frequency of the mirroring is 1 millisecond)
    *
    * @param json input JSON
    */
  def mirrorJson(json: String): Unit = {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val directory = new File(dirNamePattern.format(now))
    directory.mkdir()
    val pw = new PrintWriter(new File(s"${ directory.getName }/${ fileNamePattern.format(now) }.json"))
    Try { pw.write(json) }
    pw.close()
  }
}
