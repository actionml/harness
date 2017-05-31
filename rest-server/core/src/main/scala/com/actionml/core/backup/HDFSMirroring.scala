package com.actionml.core.backup

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

/**
  * TODO scaladoc
  */
object HDFSMirroring extends Mirroring {
  def write(uri: String, filePath: String, data: Array[Byte]): Unit = {
    System.setProperty("hadoop.home.dir", "/")
    System.setProperty("HADOOP_USER_NAME", "hdfs")
    val path = new Path(filePath)
    val conf = new Configuration()
    conf.set("fs.defaultFS", uri)
    val fs = FileSystem.get(conf)
    val os = fs.create(path)
    os.write(data)
    fs.close()
  }

  //write("hdfs://localhost:9000", "test.txt", "Hello World".getBytes)

  override def mirrorJson(json: String): Unit = ???
}
