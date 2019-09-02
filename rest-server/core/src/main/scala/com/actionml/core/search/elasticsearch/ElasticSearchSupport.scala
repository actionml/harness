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

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.time.Instant

import com.actionml.core.search.Filter.{Conditions, Types}
import com.actionml.core.search._
import com.actionml.core.validate.{CustomFormats, JsonSupport}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.util.EntityUtils
import org.apache.spark.rdd.RDD
import org.elasticsearch.client.{Request, RestClient}
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.json4s.DefaultReaders._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, JValue, _}

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.reflect.ManifestFactory
import scala.util.control.NonFatal
import scala.util.Try

trait ElasticSearchSupport extends SearchSupport[Hit] {
  override def createSearchClient(aliasName: String): SearchClient[Hit] = ElasticSearchClient(aliasName)
}

trait JsonSearchResultTransformation[T] {
  implicit def reader: Reader[T]
  implicit def manifest: Manifest[T]
  def transform(j: JValue): Seq[T]
}


trait ElasticSearchResultTransformation extends JsonSearchResultTransformation[Hit] {
  override implicit val manifest: Manifest[Hit] = ManifestFactory.classType(classOf[Hit])
  override implicit val reader: Reader[Hit] =  new Reader[Hit] {
    def read(value: JValue): Hit = value match {
      case JObject(fields) if fields.exists(_._1 == "_id") && fields.exists(_._1 == "_score") =>
        Hit(fields.find(_._1 == "_id").get._2.as[String], fields.find(_._1 == "_score").get._2.as[Float])
      case x =>
        throw new MappingException("Can't convert %s to Hit." format x)
    }
  }
  override def transform(j: JValue): Seq[Hit] = {
    (j \ "hits" \ "hits").as[Seq[Hit]]
  }
}

class ElasticSearchClient[T] private (alias: String)(implicit w: Writer[T]) extends SearchClient[T] with WriteToEsSupport with JsonSupport {
  this: JsonSearchResultTransformation[T] =>
  import ElasticSearchClient._
  import ElasticSearchClient.ESVersions._
  implicit val _ = DefaultFormats
  private val indexType = if (esVersion != v7) "items" else "_doc"

  override def close: Unit = client.close()

  override def createIndex(
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)],
    refresh: Boolean): Boolean = {
    val indexName = createIndexName(alias)
    createIndexByName(indexName, indexType, fieldNames, typeMappings, refresh)
  }

  override def saveOneById(id: String, doc: T): Boolean = {
    client.performRequest(new Request(
      "HEAD",
      s"/_alias/$alias"))
      .getStatusLine
      .getStatusCode match {
      case 200 =>
        try {
          val request = new Request("POST", s"/$alias/$indexType/${encodeURIFragment(id)}/_update")
          request.setJsonEntity(
            JsonMethods.compact(JObject(
              "doc" -> JsonMethods.asJValue(doc),
              "doc_as_upsert" -> JBool(true)
            )))
          val aliasResponse = client.performRequest(request)
          val responseJValue = parse(StringInput(EntityUtils.toString(aliasResponse.getEntity)), false)
          (responseJValue \ "result").getAs[String].contains("updated")
        } catch {
          case NonFatal(e) =>
            logger.error(s"Can't upsert $doc with id $id", e)
            false
        }
      case _ => false
    }
  }

  override def deleteIndex(refresh: Boolean): Boolean = {
    // todo: Andrey, this is a deprecated API, also this throws an exception when the Elasticsearch server is not running
    // it should give a more friendly error message by testing to see if Elasticsearch is running or maybe we should test
    // the required services when an engine is created or updated. This would be more efficient for frequent client requests
    client.performRequest(new Request("HEAD", s"/_alias/$alias")) // Does the alias exist?
      .getStatusLine
      .getStatusCode match {
      case 200 =>
        val aliasResponse = client.performRequest(new Request("GET", s"/_alias/$alias"))
        val responseJValue = parse(StringInput(EntityUtils.toString(aliasResponse.getEntity)), false)
        val indexSet = responseJValue.extract[Map[String, JValue]].keys
        indexSet.forall(deleteIndexByName(_, refresh))
      case _ => false
    }
  }

  override def search(query: SearchQuery): Seq[T] = {
    client.performRequest(new Request("HEAD", s"/_alias/$alias")) // Does the alias exist?
      .getStatusLine
      .getStatusCode match {
      case 200 =>
        // logger.info(s"Query JSON:\n${prettify(mkElasticQueryString(query))}")
        val aliasResponse = client.performRequest(new Request("GET", s"/_alias/$alias"))
        val responseJValue = parse(StringInput(EntityUtils.toString(aliasResponse.getEntity)), false)
        val indexSet = responseJValue.extract[Map[String, JValue]].keys
        indexSet.headOption.fold(Seq.empty[T]) { actualIndexName =>
          logger.debug(s"Query for alias $alias and index $actualIndexName:\n$query")
          val request = new Request("POST", s"/$actualIndexName/_search")
          request.setJsonEntity(mkElasticQueryString(query))
          val response = client.performRequest(request)
          response.getStatusLine.getStatusCode match {
            case 200 =>
              logger.info(s"Got source from query: $query")
              transform(parse(StringInput(EntityUtils.toString(response.getEntity)), false))
            case _ =>
              logger.info(s"Query: $query\nproduced status code: ${response.getStatusLine.getStatusCode}")
              Seq.empty[T]
          }
        }
      case _ => Seq.empty
    }

  }

  def findDocById(id: String): (String, Map[String, Seq[String]]) = {
    var rjv: Option[JValue] = Some(JString(""))
    try {
      client.performRequest(new Request("HEAD", s"/_alias/$alias")) // Does the alias exist?
        .getStatusLine
        .getStatusCode match {
        case 200 =>
          val aliasResponse = client.performRequest(new Request("GET", s"/_alias/$alias"))
          val responseJValue = parse(StringInput(EntityUtils.toString(aliasResponse.getEntity)), false)
          val indexSet = responseJValue.extract[Map[String, JValue]].keys
          rjv = indexSet.headOption.fold(Option.empty[JValue]) { actualIndexName =>
            val url = s"/$actualIndexName/$indexType/${encodeURIFragment(id)}"
            logger.info(s"find doc by id using URL: $url")
            val response = client.performRequest(new Request("GET", url))
            logger.info(s"got response: $response")

            response.getStatusLine.getStatusCode match {
              case 200 =>
                val entity = EntityUtils.toString(response.getEntity)
                logger.info(s"got status code: 200\nentity: $entity")
                if (entity.isEmpty) {
                  None
                } else {
                  logger.info(s"About to parse: $entity")
                  val result = parse(StringInput(entity), false)
                  logger.info(s"getSource for $url result: $result")
                  Some(result)
                }
              case 404 =>
                logger.info(s"got status code: 404")
                Some(parse(StringInput("""{"notFound": "true"}"""), false))
              case _ =>
                logger.info(s"got status code: ${response.getStatusLine.getStatusCode}\nentity: ${EntityUtils.toString(response.getEntity)}")
                None
            }
          }
        case _ =>
      }
    } catch {
      case e: org.elasticsearch.client.ResponseException => {
        logger.error("got no data for the item", e)
        rjv = None
      }
      case NonFatal(e) =>
        logger.error("got unknown exception and so no data for the item", e)
        rjv = None
    }

    if (rjv.nonEmpty) {
      //val responseJValue = parse(EntityUtils.toString(response.getEntity))
      try {
        val result = (rjv.get \ "_source").values.asInstanceOf[Map[String, List[String]]]
        id -> result
      } catch {
        case e: ClassCastException =>
          id -> Map.empty
      }
    } else {
      logger.info(s"Non-existent item $id, but that's ok, return backfill recs")
      id -> Map.empty
    }
  }

  override def hotSwap(
    indexRDD: RDD[Map[String, Any]],
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)],
    numESWriteConnections: Option[Int] = None): Unit = {
    import org.elasticsearch.spark._
    val newIndex = createIndexName(alias)

    logger.info(s"Create new index: $newIndex, $fieldNames, $typeMappings")
    // todo: this should have a typeMappings that is Map[String, (String, Boolean)] with the Boolean saying to use norms
    // taken out for now since there is no client.admin in the REST client. Have to construct REST call directly
    createIndexByName(newIndex, indexType, fieldNames, typeMappings, refresh = false, doNotLinkAlias = true)

    // throttle writing to the max bulk-write connections, which is one per ES core.
    // todo: can we find this from the cluster itself?
    val repartitionedIndexRDD = if (numESWriteConnections.nonEmpty && indexRDD.context.defaultParallelism >
      numESWriteConnections.get) {
      logger.info(s"defaultParallelism: ${indexRDD.context.defaultParallelism}")
      logger.info(s"Coalesce to: ${numESWriteConnections.get} to reduce number of ES connections for saveToEs")
      indexRDD.coalesce(numESWriteConnections.get)
    } else {
      logger.info(s"Number of ES connections for saveToEs: ${indexRDD.context.defaultParallelism}")
      indexRDD
    }

    val newIndexURI = "/" + newIndex + "/" + indexType
    val esConfig = Map("es.mapping.id" -> "id")
    repartitionedIndexRDD.saveToEs(newIndexURI, esConfig)

    val (oldIndexSet, deleteOldIndexQuery) = client.performRequest(new Request("HEAD", s"/_alias/$alias")) // Does the alias exist?
      .getStatusLine.getStatusCode match {
        case 200 => {
          val response = client.performRequest(new Request("GET", s"/_alias/$alias"))
          val responseJValue = parse(StringInput(EntityUtils.toString(response.getEntity)), false)
          val oldIndexSet = responseJValue.extract[Map[String, JValue]].keys
          val oldIndexName = oldIndexSet.head
          client.performRequest(new Request("HEAD", s"/$oldIndexName")) // Does the old index exist?
            .getStatusLine.getStatusCode match {
              case 200 => {
                val deleteOldIndexQuery = s""",{ "remove_index": { "index": "$oldIndexName"}}"""
                (oldIndexSet, deleteOldIndexQuery)
              }
              case _ => (Set(), "")
            }
        }
        case _ => (Set(), "")
      }

    val aliasQuery =
      s"""
        |{
        |    "actions" : [
        |        { "add":  { "index": "$newIndex", "alias": "$alias" } }
        |        $deleteOldIndexQuery
        |    ]
        |}
      """.stripMargin.replace("\n", "")

    val request = new Request( "POST", "/_aliases")
    request.setJsonEntity(aliasQuery)
    client.performRequest(request)
    oldIndexSet.foreach(deleteIndexByName(_, refresh = false))
  }


  private def encodeURIFragment(s: String): String = {
    var result: String = ""
    try
      result = URLEncoder.encode(s, "UTF-8")
        .replaceAll("\\+", "%20")
        .replaceAll("\\%21", "!")
        .replaceAll("\\%27", "'")
        .replaceAll("\\%28", "(")
        .replaceAll("\\%29", ")")
        .replaceAll("\\%7E", "%7E")
    catch {
      case e: UnsupportedEncodingException =>
        result = s
    }
    result
  }

  private def createIndexByName(
    indexName: String,
    indexType: String,
    fieldNames: List[String],
    typeMappings: Map[String, (String, Boolean)],
    refresh: Boolean,
    doNotLinkAlias: Boolean = false): Boolean = {
    def fieldToJson: ((String, (String, Boolean))) => JField = {
      case (name, (tpe, _)) => JField(name, JObject("type" -> JString(tpe)))
    }
    client.performRequest(new Request("HEAD", s"/$indexName"))
      .getStatusLine.getStatusCode match {
        case 404 => { // should always be a unique index name so fail unless we get a 404
          val body = JObject(
            JField("mappings",
              JObject(indexType ->
                JObject("properties" -> {
                  fieldNames.map { fieldName =>
                    if (typeMappings.contains(fieldName))
                      JObject(fieldName -> JObject("type" -> JString(typeMappings(fieldName)._1)))
                    else // unspecified fields are treated as not_analyzed strings
                      JObject(fieldName -> JObject("type" -> JString("keyword")))
                  }.reduce(_ ~ _)
                })
              )
            ) :: (if (doNotLinkAlias) Nil else List(JField("aliases", JObject(alias -> JObject()))))
          )

          val request = new Request("PUT", s"/$indexName")
          if (esVersion == v7) request.addParameter("include_type_name", "true")
          request.setJsonEntity(JsonMethods.compact(body))
          client.performRequest(request)
            .getStatusLine.
            getStatusCode match {
            case 200 =>
              // now refresh to get it 'committed'
              // todo: should do this after the new index is created so no index downtime
              if (refresh) refreshIndexByName(indexName)
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

  private def deleteIndexByName(indexName: String, refresh: Boolean): Boolean = {
    client.performRequest(new Request("HEAD", s"/$indexName"))
      .getStatusLine.getStatusCode match {
      case 404 => false
      case 200 =>
        client.performRequest(new Request("DELETE", s"/$indexName"))
          .getStatusLine.getStatusCode match {
          case 200 =>
            if (refresh) refreshIndexByName(indexName)
          case _ =>
            logger.info(s"Index $indexName wasn't deleted, but may have quietly failed.")
        }
        true
      case _ =>
        throw new IllegalStateException()
        false
    }
  }

  private def refreshIndexByName(indexName: String): Unit = {
    client.performRequest(new Request("POST", s"/$indexName/_refresh"))
  }
}


object ElasticSearchClient extends JsonSupport {
  private implicit val _: Writer[Hit] = new Writer[Hit] {
    override def write(obj: Hit): JValue = JObject(
      "id" -> JString(obj.id),
      "score" -> JDouble(obj.score)
    )
  }

  def apply(aliasName: String): ElasticSearchClient[Hit] = new ElasticSearchClient[Hit](aliasName) with ElasticSearchResultTransformation


  private def createIndexName(alias: String) = alias + "_" + Instant.now().toEpochMilli.toString

  private lazy val config: Config = ConfigFactory.load() // todo: use ficus or something

  private lazy val client: RestClient = {
    val builder = RestClient.builder(
      new HttpHost(
        config.getString("elasticsearch.host"),
        config.getInt("elasticsearch.port"),
        config.getString("elasticsearch.protocol")))

    if (config.hasPath("elasticsearch.auth")) {
      val authConfig = config.getConfig("elasticsearch.auth")
      builder.setHttpClientConfigCallback(new BasicAuthProvider(
        authConfig.getString("username"),
        authConfig.getString("password")
      ))
    }
    builder.build
  }

  private lazy val esVersion: ESVersions.Value = {
    import ESVersions._
    Try(client.performRequest(new Request("GET", "/"))).toOption
      .flatMap { r =>
        val version = (JsonMethods.parse(StreamInput(r.getEntity.getContent), false) \ "version" \ "number").as[String]
        if (version.startsWith("5.")) Some(v5)
        else if (version.startsWith("6.")) Some(v6)
        else if (version.startsWith("7.")) Some(v7)
        else {
          logger.warn("Unsupported Elastic Search version: $version")
          Option.empty
        }
      }.getOrElse(v5)
  }
  private object ESVersions extends Enumeration {
    val v5 = Value(5)
    val v6 = Value(6)
    val v7 = Value(7)
  }

  private[elasticsearch] def mkElasticQueryString(query: SearchQuery): String = {
    import org.json4s.jackson.JsonMethods._
    var clauses = JObject()
    if (esVersion == ESVersions.v5) {
      clauses = clauses ~ ("must" -> matcherToJson(Map("terms" -> query.must)))
      clauses = clauses ~ ("must_not" -> matcherToJson(Map("terms" -> query.mustNot)))
      clauses = clauses ~ ("filter" -> filterToJson(query.filters))
      clauses = clauses ~ ("should" -> matcherToJson(Map("terms" -> query.should), "constant_score" -> JObject("filter" -> ("match_all" -> JObject()), "boost" -> 0)))
      if (query.must.flatMap(_.values).isEmpty) clauses = clauses ~ ("minimum_should_match" -> 1)
    } else {
      clauses = clauses ~ ("must" -> mustToJson(query.must))
      clauses = clauses ~ ("must_not" -> clausesToJson(Map("term" -> query.mustNot)))
      clauses = clauses ~ ("filter" -> filterToJson(query.filters))
      clauses = clauses ~ ("should" -> clausesToJson(Map("term" -> query.should)))
      if (query.must.flatMap(_.values).nonEmpty) clauses = clauses ~ ("minimum_should_match" -> JInt(1))
    }
    val esQuery =
      ("size" -> query.size) ~
      ("from" -> query.from) ~
      ("query" -> JObject("bool" -> clauses)) ~
      ("sort" -> Seq(
        "_score" -> JObject("order" -> JString("desc")),
        query.sortBy -> (("unmapped_type" -> "double") ~ ("order" -> "desc"))
      ))
    compact(render(esQuery)) }

  private def mustToJson: Seq[Matcher] => JValue = {
    case Nil =>
      JObject {
        "match_all" -> JObject("boost" -> JDouble(0))
      }
    case clauses =>
      clausesToJson(Map("term" -> clauses))
  }

  private def clausesToJson(clauses: Map[String, Seq[Matcher]], others: (String, JObject)*): JArray = {
    clauses.toList.flatMap { case (clause, matchers) =>
      matchers.flatMap { m =>
        m.values.map { v =>
          clause -> JObject {
            m.name ->
              ("value" -> JString(v)) ~
              m.boost.fold[JObject](JObject())(b => JObject("boost" -> JDouble(b)))
          }
        }
      }
    } ++ others.toList
  }

  private def matcherToJson(clauses: Map[String, Seq[Matcher]], others: (String, JObject)*): JArray = {
    clauses.map { case (clause, matchers) =>
      matchers.map { m =>
        clause -> (m.name -> m.values) ~ m.boost.fold(JObject())("boost" -> _)
      }
    }.flatten.toList ++ others.toList
  }

  private[elasticsearch] def filterToJson(filters: Seq[Filter]): JArray = {
    implicit val _ = CustomFormats
    filters.foldLeft(Map.empty[(Types.Type, String), JObject]) { case (acc, f) =>
      acc.get(f.`type` -> f.name).fold {
        acc + ((f.`type` -> f.name) -> JObject(
          f.name -> (if (f.condition == Conditions.eq) Extraction.decompose(f.value)
          else JObject(f.condition.toString -> Extraction.decompose(f.value)))
        ))
      } { j =>
        acc + ((f.`type` -> f.name) -> j.merge(JObject(
          f.name -> (if (f.condition == Conditions.eq) Extraction.decompose(f.value)
          else JObject(f.condition.toString -> Extraction.decompose(f.value)))
        )))
      }
    }
  }.toList.map {
    case ((t, _), j) => JObject(t.toString -> j)
  }

  private class BasicAuthProvider(username: String, password: String) extends HttpClientConfigCallback {
    private val credentialsProvider = new BasicCredentialsProvider()
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))

    override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
      httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
    }
  }
}
