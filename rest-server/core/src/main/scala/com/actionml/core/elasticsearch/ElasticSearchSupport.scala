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

package com.actionml.core.elasticsearch

import com.actionml.core.search.{SearchClient, SearchSupport}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.json4s.{DefaultFormats, JValue}
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._

trait ElasticSearchSupport extends SearchSupport[JValue] {
  override def createSearchClient(hosts: String*): SearchClient[JValue] = ElasticSearchClient(hosts:_*)
}

class ElasticSearchClient private (hosts: Seq[String]) extends SearchClient[JValue] with LazyLogging {

  private val config = ConfigFactory.load()
  private val client: RestClient = {
    import ElasticSearchClient._
    val builder = RestClient.builder()
    val authConfig = config.atKey("elasticsearch.auth")
    if (!authConfig.isEmpty) {
      builder.setHttpClientConfigCallback(new BasicAuthProvider(
        authConfig.getString("username"),
        authConfig.getString("password")
      ))
    }
    builder.build
  }

  override def close: Unit = client.close()

  override def createIndex(indexName: String,
                           indexType: String,
                           fieldNames: List[String],
                           typeMappings: Map[String, (String, Boolean)],
                           refresh: Boolean): Boolean = {
    client.performRequest(
      "HEAD",
      s"/$indexName",
      Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
      case 404 => { // should always be a unique index name so fail unless we get a 404
        var mappings =
          s"""
             |{ "mappings": {
             |    "$indexType": {
             |      "properties": {
            """.stripMargin.replace("\n", "")

        def mappingsField(`type`: String) = {
          s"""
             |    : {
             |      "type": "${`type`}"
             |    },
            """.stripMargin.replace("\n", "")
        }

        val mappingsTail =
        // unused mapping forces the last element to have no comma, fuck JSON
          s"""
             |    "last": {
             |      "type": "keyword"
             |    }
             |}}}}
            """.stripMargin.replace("\n", "")

        fieldNames.foreach { fieldName =>
          if (typeMappings.contains(fieldName))
            mappings +=
              (s""""$fieldName"""" + mappingsField(typeMappings(
                fieldName)._1))
          else // unspecified fields are treated as not_analyzed strings
            mappings += (s""""$fieldName"""" + mappingsField(
              "keyword"))
        }

        mappings += mappingsTail
        // "id" string is not_analyzed and does not use norms
        // val entity = new NStringEntity(mappings, ContentType.APPLICATION_JSON)
        //logger.info(s"Create index with:\n$indexName\n$mappings\n")
        val entity = new NStringEntity(
          mappings,
          ContentType.
            APPLICATION_JSON)

        client.
          performRequest(
            "PUT",
            s"/$indexName",
            Map.empty[String, String].asJava,
            entity).getStatusLine.
          getStatusCode match {
          case 200 =>
            // now refresh to get it 'committed'
            // todo: should do this after the new index is created so no index downtime
            if (refresh) refreshIndex(indexName)
          case _ =>
            logger.info(s"Index $indexName wasn't created, but may have quietly failed.")
        }
        true
      }
      case 200 =>
        logger.warn(s"Elasticsearch index: $indexName wasn't created because it already exists. " +
          s"This may be an error. Leaving the old index active.")
        false
      case _ =>
        throw new IllegalStateException(s"/$indexName is invalid.")
        false
    }
  }

  override def deleteIndex(indexName: String, refresh: Boolean): Boolean = {
    client.performRequest(
      "HEAD",
      s"/$indexName",
      Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
      case 404 => false
      case 200 =>
        client.performRequest(
          "DELETE",
          s"/$indexName",
          Map.empty[String, String].asJava).getStatusLine.getStatusCode match {
          case 200 =>
            if (refresh) refreshIndex(indexName)
          case _ =>
            logger.info(s"Index $indexName wasn't deleted, but may have quietly failed.")
        }
        true
      case _ =>
        throw new IllegalStateException()
        false
    }
  }

  override def refreshIndex(indexName: String): Unit =
    client.performRequest(
      "POST",
      s"/$indexName/_refresh",
      Map.empty[String, String].asJava)

  override def search(query: String, indexName: String): Option[JValue] = {
    implicit val _ = DefaultFormats
    logger.info(s"Query:\n$query")
    val response = client.performRequest(
      "POST",
      s"/$indexName/_search",
      Map.empty[String, String].asJava,
      new StringEntity(query, ContentType.APPLICATION_JSON))
    response.getStatusLine.getStatusCode match {
      case 200 =>
        logger.info(s"Got source from query: $query")
        Some(parse(EntityUtils.toString(response.getEntity)))
      case _ =>
        logger.info(s"Query: $query\nproduced status code: ${response.getStatusLine.getStatusCode}")
        None
    }
  }
}


object ElasticSearchClient {

  def apply(hosts: String*): ElasticSearchClient = new ElasticSearchClient(hosts)


  private class BasicAuthProvider(username: String, password: String) extends HttpClientConfigCallback {
    private val credentialsProvider = new BasicCredentialsProvider()
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

    override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
    }
  }
}
