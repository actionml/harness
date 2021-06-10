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
package com.actionml.core.search.elasticsearch

import org.scalatest.{FlatSpec, Matchers}

class ElasticsearchSupportTest extends FlatSpec with Matchers {

  "parseNodes" should "parse single node" in {
    val nodes = ElasticSearchClient.parseNodes("http://localhost:9200")
    nodes.size shouldEqual 1
    val host = nodes.head.getHost
    host.getHostName shouldEqual "localhost"
    host.getPort shouldEqual 9200
    host.getSchemeName shouldEqual "http"
  }

  it should "parse multiple nodes" in {
    val nodes = ElasticSearchClient.parseNodes("https://localhost:9200,anotherhost:9200,thirdone:9100")
    nodes.size shouldEqual 3

    val host1 = nodes.head.getHost
    host1.getHostName shouldEqual "localhost"
    host1.getPort shouldEqual 9200
    host1.getSchemeName shouldEqual "https"

    val host2 = nodes(1).getHost
    host2.getHostName shouldEqual "anotherhost"
    host2.getPort shouldEqual 9200
    host2.getSchemeName shouldEqual "https"

    val host3 = nodes(2).getHost
    host3.getHostName shouldEqual "thirdone"
    host3.getPort shouldEqual 9100
    host3.getSchemeName shouldEqual "https"
  }

  it should "parse http scheme" in {
    val nodes = ElasticSearchClient.parseNodes("http://localhost:9200")
    nodes.foreach(_.getHost.getSchemeName shouldEqual "http")
  }

  it should "parse https scheme" in {
    val nodes = ElasticSearchClient.parseNodes("https://localhost:9200,anotherhost:9200")
    nodes.foreach(_.getHost.getSchemeName shouldEqual "https")
  }

  it should "not parse invalid protocols" in {
    assertThrows[RuntimeException](ElasticSearchClient.parseNodes("hdfsk99ps://localhost:9200,anotherhost:9200"))
  }

  it should "not parse invalid port numbers" in {
    assertThrows[RuntimeException](ElasticSearchClient.parseNodes("https://localhost:qqqq,anotherhost:9200"))
  }

}
