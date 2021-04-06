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

import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}


object HDFSFactory {
  private lazy val conf: Configuration = {
    val configDir = sys.env.getOrElse("HARNESS_HDFS_CONF_DIR", throw new RuntimeException("HARNESS_HDFS_CONF_DIR should be set to use HDFS"))
    val config = new Configuration()
    config.setBoolean("dfs.support.append", true) // used by mirroring
    // the following are the minimum needed from the hdfs setup files even if hdfs is not setup
    // on this machine these must be copied from the namenode/master
    config.addResource(new Path(s"${configDir}/core-site.xml"))
    config.addResource(new Path(s"${configDir}/hdfs-site.xml"))
    config
  }

  lazy val hdfs: FileSystem = FileSystem.get(conf)

  def newInstance(uri: URI): FileSystem = FileSystem.get(uri, new Configuration())
}