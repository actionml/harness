/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.core.backup

import com.typesafe.config.ConfigFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase


object HDFSFactory {


  case class HDFSConfig(master: String, port: Int, configDir: String)

  private lazy val config = ConfigFactory.load

  private lazy val hdfsConf = config.as[HDFSConfig]("hdfs")

  //private lazy val hdfsConfDir = sys.env.getOrElse("HDFS_CONF_DIR", "/usr/local/hadoop/etc/hadoop")

  lazy val conf: Configuration = {
    val config = new Configuration()
    config.setBoolean("dfs.support.append", true) // used by mirroring
    // the following are the minimum needed from the hdfs setup files even if hdfs is not setup
    // on this machine these must be copied from the namenode/master
    config.addResource(new Path(s"${hdfsConf.configDir}/core-site.xml"))
    config.addResource(new Path(s"${hdfsConf.configDir}/hdfs-site.xml"))
    config
  }

  lazy val hdfs: FileSystem = FileSystem.get(conf)
}
