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
import java.net.{URI, URLEncoder}
import java.time.Instant

import com.actionml.core.search.Filter.{Conditions, Types}
import com.actionml.core.search._
import com.actionml.core.search.elasticsearch.ElasticSearchSupport.EsDocument
import com.actionml.core.validate.JsonSupport
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.http.HttpHost
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.util.EntityUtils
import org.apache.spark.rdd.RDD
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import org.elasticsearch.client.{Request, Response, ResponseListener, RestClient}
import org.json4s.DefaultReaders._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, JValue, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.reflect.ManifestFactory
import scala.util.control.NonFatal
import scala.util.{Properties, Success, Try}

trait ElasticSearchSupport extends SearchSupport[Hit, EsDocument] {
  override def createSearchClient(aliasName: String): SearchClient[Hit, EsDocument] = ElasticSearchClient(aliasName)
}

object ElasticSearchSupport {
  type EsDocument = (String, Map[String, List[String]])
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

class ElasticSearchClient private (alias: String)(implicit w: Writer[EsDocument])
  extends SearchClient[Hit, EsDocument] with LazyLogging with WriteToEsSupport with JsonSupport {
  this: JsonSearchResultTransformation[Hit] =>
  import ElasticSearchClient.ESVersions._
  import ElasticSearchClient._
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

  override def saveOneById(id: String, doc: EsDocument): Boolean = {
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
          val response = client.performRequest(request)
          val responseJValue = parse(EntityUtils.toString(response.getEntity))
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
        val responseJValue = parse(EntityUtils.toString(aliasResponse.getEntity))
        val indexSet = responseJValue.extract[Map[String, JValue]].keys
        indexSet.forall(deleteIndexByName(_, refresh))
      case _ => false
    }
  }

  override def search(query: SearchQuery): Future[Seq[Hit]] = {
    val p = Promise[Seq[Hit]]()
    val aliasesListener = new ResponseListener {
      override def onSuccess(response: Response): Unit = {
        response.getStatusLine.getStatusCode match {
          case 200 =>
            val responseJValue = parse(EntityUtils.toString(response.getEntity))
            val indexSet = responseJValue.extract[Map[String, JValue]].keys
            indexSet.headOption.foreach { actualIndexName =>
              logger.debug(s"Query for alias $alias and index $actualIndexName:\n$query")
              val request = new Request("POST", s"/$actualIndexName/_search")
              request.setJsonEntity(mkElasticQueryString(query))

              val searchListener = new ResponseListener {
                override def onSuccess(response: Response): Unit = {
                  response.getStatusLine.getStatusCode match {
                    case 200 =>
                      logger.trace(s"Got source from query: $query")
                      p.complete(Try(transform(parse(EntityUtils.toString(response.getEntity)))))
                    case _ =>
                      logger.trace(s"Query: $query\nproduced status code: ${response.getStatusLine.getStatusCode}")
                      p.complete(Success(Seq.empty[Hit]))
                  }
                }
                override def onFailure(exception: Exception): Unit = p.failure(exception)
              }
              client.performRequestAsync(request, searchListener)
            }
          case _ => Seq.empty[Hit]
        }
      }
      override def onFailure(exception: Exception): Unit = p.failure(exception)
    }
    client.performRequestAsync(new Request("GET", s"/_alias/$alias"), aliasesListener)
    p.future
  }

  private def performRequest(method: String, url: String): Future[Response] = {
    Future(client.performRequest(new Request(method, url)))
  }

  def findDocById(id: String): EsDocument = {
    var rjv: Option[JValue] = None
    try {
      client.performRequest(new Request("HEAD", s"/_alias/$alias")) // Does the alias exist?
        .getStatusLine
        .getStatusCode match {
        case 200 =>
          val aliasResponse = client.performRequest(new Request("GET", s"/_alias/$alias"))
          val responseJValue = parse(EntityUtils.toString(aliasResponse.getEntity))
          val indexSet = responseJValue.extract[Map[String, JValue]].keys
          rjv = indexSet.headOption.map { actualIndexName =>
            val url = s"/$actualIndexName/$indexType/${encodeURIFragment(id)}"
            logger.trace(s"Find doc by id using URL: $url")
            val response = client.performRequest(new Request("GET", url))
            logger.trace(s"Got response: $response")

            response.getStatusLine.getStatusCode match {
              case 200 =>
                val entity = EntityUtils.toString(response.getEntity)
                logger.trace(s"Got status code: 200\nentity: $entity")
                if (entity.isEmpty) {
                  Option.empty[JValue]
                } else {
                  logger.trace(s"About to parse: $entity")
                  val result = parse(entity)
                  logger.trace(s"GetSource for $url result: $result")
                  Some(result)
                }
              case 404 =>
                logger.trace(s"Got status code: 404")
                Some(parse("""{"notFound": "true"}"""))
              case _ =>
                logger.trace(s"Got status code: ${response.getStatusLine.getStatusCode}\nentity: ${EntityUtils.toString(response.getEntity)}")
                Option.empty[JValue]
            }
          }
        case _ =>
      }
    } catch {
      case e: org.elasticsearch.client.ResponseException =>
        if (e.getResponse.getStatusLine.getStatusCode == 404) logger.debug(s"got no data for the item because of $e - ${e.getResponse.getStatusLine.getReasonPhrase}")
        else logger.error("Find doc by id error", e)
        rjv = None
      case NonFatal(e) =>
        logger.error("got unknown exception and so no data for the item", e)
        rjv = None
    }

    if (rjv.nonEmpty) {
      try {
        val jobj = (rjv.get \ "_source").values.asInstanceOf[Map[String, Any]]
        id -> jobj.collect {
          case (name, value) if value.isInstanceOf[List[Any]] =>
            name -> value.asInstanceOf[List[Any]].collect {
              case v: String => v
            }
        }
      } catch {
        case e: ClassCastException =>
          logger.error("Wrong format of ES doc", e)
          id -> Map.empty[String, List[String]]
      }
    } else {
      logger.debug(s"Non-existent item-id: $id, creating new item.")
      id -> Map.empty[String, List[String]]
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
          val responseJValue = parse(EntityUtils.toString(response.getEntity))
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
              logger.warn(s"Index $indexName wasn't created, but may have quietly failed.")
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
            logger.warn(s"Index $indexName wasn't deleted, but may have quietly failed.")
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


object ElasticSearchClient extends LazyLogging with JsonSupport {
  private implicit val _: Writer[EsDocument] = new Writer[EsDocument] {
    override def write(doc: EsDocument): JValue = JObject(
      doc._2.foldLeft[List[JField]](List.empty[JField]) { case (acc, (key, values)) =>
        JField(key, JArray(values.map(JString))) :: acc
      }
    )
  }

  def apply(aliasName: String): ElasticSearchClient =
    new ElasticSearchClient(aliasName) with ElasticSearchResultTransformation


  private def createIndexName(alias: String) = alias + "_" + Instant.now().toEpochMilli.toString

  private lazy val config: Config = ConfigFactory.load() // todo: use ficus or something

  private lazy val client: RestClient = {
    val uri = new URI(Properties.envOrElse("ELASTICSEARCH_URI", "http://localhost:9200" ))

    val builder = RestClient.builder(
      new HttpHost(
        uri.getHost,
        uri.getPort,
        uri.getScheme))

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
        val version = (JsonMethods.parse(r.getEntity.getContent) \ "version" \ "number").as[String]
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
    val mustIsEmpty = query.must.flatMap(_.values).isEmpty
    val shouldNonEmpty = query.should.flatMap(_.values).nonEmpty
    if (esVersion == ESVersions.v5) {
      clauses = clauses ~ ("must" -> matcherToJson(Map("terms" -> query.must)))
      clauses = clauses ~ ("must_not" -> matcherToJson(Map("terms" -> query.mustNot)))
      clauses = clauses ~ ("filter" -> filterToJson(query.filters))
      clauses = clauses ~ ("should" -> matcherToJson(Map("terms" -> query.should), "constant_score" -> JObject("filter" -> ("match_all" -> JObject()), "boost" -> 0)))
      if (mustIsEmpty) clauses = clauses ~ ("minimum_should_match" -> 1)
    } else {
      clauses = clauses ~ ("must" -> mustToJson(query.must))
      clauses = clauses ~ ("must_not" -> clausesToJson(Map("term" -> query.mustNot)))
      clauses = clauses ~ ("filter" -> filterToJson(query.filters))
      clauses = clauses ~ ("should" -> clausesToJson(Map("term" -> query.should)))
      if (mustIsEmpty && shouldNonEmpty) clauses = clauses ~ ("minimum_should_match" -> JInt(1))
    }
    val esQuery =
      ("size" -> query.size) ~
      ("from" -> query.from) ~
      ("query" -> JObject("bool" -> clauses)) ~
      ("sort" -> Seq(
        "_score" -> JObject("order" -> JString("desc")),
        query.sortBy -> (("unmapped_type" -> "double") ~ ("order" -> "desc"))
      ))
    val esquery = compact(render(esQuery))
    logger.debug(s"Query for Elasticsearch: $esquery")
    esquery
  }

  private def mustToJson: Seq[Matcher] => JValue = clauses =>
    if (clauses.isEmpty)
      JObject("match_all" -> JObject("boost" -> JDouble(0)))
    else
      clausesToJson(Map("term" -> clauses), mkAND = false)

  private def clausesToJson(clauses: Map[String, Seq[Matcher]], mkAND: Boolean = true): JArray = {
    def mkClause(clause: String, m: Matcher, v: String): (String, JObject) = {
      clause -> JObject {
        m.name ->
          ("value" -> JString(v)) ~
            m.boost.fold[JObject](JObject())(b => JObject("boost" -> JDouble(b)))
      }
    }
    clauses.view.flatMap { case (clause, matchers) =>
      matchers.flatMap {
        case m@Matcher(_, values, _) if mkAND || values.isEmpty || values.size == 1 =>
          m.values.map { v =>
            mkClause(clause, m, v)
          }
        case m => Seq("bool" -> JObject("should" -> JArray(
          m.values.map { v => JObject(
            mkClause(clause, m, v)
          )}.toList
        )))
      }
    }
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
