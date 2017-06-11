package com.actionml.core.backup

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

/**
  * TODO scaladoc
  */
object HDFSMirroring extends Mirroring {
  System.setProperty("hadoop.home.dir", "/")
  System.setProperty("HADOOP_USER_NAME", System.getProperty("user.name"))

  private val conf = new Configuration()
  conf.set("fs.defaultFS", "hdfs://localhost:9000")

  override def mirrorJson(engineId: String, json: String): Unit = {
    val fs = FileSystem.get(conf)
    try {
      fs.mkdirs(new Path(engineId))
      fs.create(new Path(s"$engineId/${ fileNamePattern.format(now) }.json")).write(json.getBytes)
    } finally {
      fs.close()
    }
  }
}
