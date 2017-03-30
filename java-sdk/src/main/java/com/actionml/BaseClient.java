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

package com.actionml;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.google.gson.*;
import scala.util.Try;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         20.02.17 20:16
 */
public class BaseClient {

    protected final ActorSystem system;
    protected final Materializer materializer;
    protected final String host;
    protected final Integer port;
    protected final Flow<Pair<HttpRequest, Long>, Pair<Try<HttpResponse>, Long>, HostConnectionPool> poolClientFlow;

    protected final JsonParser parser = new JsonParser();
    protected final GsonBuilder gsonBuilder = new GsonBuilder();

    {
        gsonBuilder.registerTypeAdapter(org.joda.time.DateTime.class, new DateTimeAdapter());
    }

    protected final Gson gson = gsonBuilder.create();

    public BaseClient(String host, Integer port) {
        system = ActorSystem.create("actionml-sdk-client");
        Function<Throwable, Supervision.Directive> decider = exc -> {
            System.err.println(exc.getMessage());
//            exc.printStackTrace();
            return Supervision.resume();
        };
        materializer = ActorMaterializer.create(
                ActorMaterializerSettings.create(system).withSupervisionStrategy(decider),
                system
        );

        this.host = host;
        this.port = port;

//        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system);
//        poolClientFlow = Http.get(system).cachedHostConnectionPool(
//                ConnectHttp.toHostHttps(host, port),
//                settings,
//                system.log(),
//                materializer);

        poolClientFlow = Http.get(system).cachedHostConnectionPool(
                ConnectHttp.toHost(host, port),
                materializer);
    }

    public CompletionStage<HttpResponse> single(HttpRequest request) {
        return Source.single(Pair.create(request, 0L))
                .via(poolClientFlow)
                .runWith(Sink.head(), materializer)
                .thenCompose(this::extractResponse)
                .thenApply(Pair::second);
    }

    protected HttpRequest createGet(Uri uri) {
        return createRequest(HttpMethods.GET, uri);
    }

    protected HttpRequest createPost(Uri uri, String json) {
        return createRequest(HttpMethods.POST, uri, json);
    }

    protected HttpRequest createDelete(Uri uri) {
        return createRequest(HttpMethods.DELETE, uri);
    }

    protected HttpRequest createRequest(HttpMethod method, Uri uri, String json) {
        return createRequest(method, uri).withEntity(ContentTypes.APPLICATION_JSON, json);
    }

    protected HttpRequest createRequest(HttpMethod method, Uri uri) {
        return HttpRequest.create().withMethod(method).withUri(uri);
    }

    protected CompletionStage<Pair<Long, HttpResponse>> extractResponse(Pair<Try<HttpResponse>, Long> pair) {
        CompletableFuture<Pair<Long, HttpResponse>> future = new CompletableFuture<>();
        Try<HttpResponse> tryResponse = pair.first();
        if (tryResponse.isSuccess()) {
            future.complete(Pair.create(pair.second(), tryResponse.get()));
        } else {
            future.completeExceptionally(tryResponse.failed().get());
        }
        return future;
    }

    protected CompletionStage<Pair<Integer, String>> extractResponse(HttpResponse response) {
        CompletableFuture<Pair<Integer, String>> future;
        future = response.entity()
                .getDataBytes()
                .runFold(ByteString.empty(), ByteString::concat, this.materializer)
                .thenApply(ByteString::utf8String)
                .thenApply(str -> {
                    Integer code = response.status().intValue();
                    return Pair.create(code, str);
                }).toCompletableFuture();

        return future;
    }

    public JsonElement toJsonElement(String json) {
        return parser.parse(json);
    }

    public <T> T toPojo(JsonElement jsonElement, Class<T> classOfT) throws JsonSyntaxException {
        return gson.fromJson(jsonElement, classOfT);
    }

    public Materializer getMaterializer() {
        return materializer;
    }

    public void close() {
        System.out.println("Shutting down client");
        Http.get(system)
                .shutdownAllConnectionPools()
                .whenComplete((s, f) -> system.terminate());
    }
}
