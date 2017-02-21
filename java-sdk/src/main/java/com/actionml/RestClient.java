package com.actionml;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.google.gson.*;
import scala.util.Try;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         05.02.17 14:21
 */
abstract class RestClient extends BaseClient{

    RestClient(String host, Integer port) {
        super(host, port);
    }

    public CompletionStage<JsonElement> get(Uri uri, String id) {
        return single(createGet(uri.addPathSegment(id))).thenCompose(this::extractJson);
    }

    public CompletionStage<List<Pair<Long, CompletionStage<JsonElement>>>> multiGet(Uri uri, List<String> ids) {
        List<HttpRequest> requests = ids.stream()
                .map(uri::addPathSegment)
                .map(this::createGet)
                .collect(Collectors.toList());

        return multi(requests).thenApply(this::extractJson);
    }

    public CompletionStage<JsonElement> put(Uri uri, String json) {
        return single(createPut(uri, json)).thenCompose(this::extractJson);
    }

    public CompletionStage<List<Pair<Long, CompletionStage<JsonElement>>>> multiPut(Uri uri, List<String> jsonList) {
        List<HttpRequest> requestList = jsonList.stream()
            .map(json -> createPut(uri, json))
            .collect(Collectors.toList());
        return multi(requestList).thenApply(this::extractJson);
    }

    public CompletionStage<JsonElement> post(Uri uri, String json) {
        return single(createPost(uri, json)).thenCompose(this::extractJson);
    }

    public CompletionStage<JsonElement> post(Uri uri, String id, String json) {
        return post(uri.addPathSegment(id), json);
    }

    public CompletionStage<List<Pair<Long, CompletionStage<JsonElement>>>> multiPost(Uri uri, List<String> jsonList) {
        List<HttpRequest> requestList = jsonList.stream()
                .map(json -> createPost(uri, json))
                .collect(Collectors.toList());
        return multi(requestList).thenApply(this::extractJson);
    }

    public CompletionStage<JsonElement> delete(Uri uri, String id) {
        return single(createDelete(uri.addPathSegment(id))).thenCompose(this::extractJson);
    }

    public CompletionStage<List<Pair<Long, CompletionStage<JsonElement>>>> multiDelete(Uri uri, List<String> ids) {
        List<HttpRequest> requests = ids.stream()
                .map(uri::addPathSegment)
                .map(this::createDelete)
                .collect(Collectors.toList());

        return multi(requests).thenApply(this::extractJson);
    }

    private HttpRequest createPost(Uri uri, String json) {
        return createRequest(HttpMethods.POST, uri, json);
    }

    private HttpRequest createPut(Uri uri, String json) {
        return createRequest(HttpMethods.PUT, uri, json);
    }

    private HttpRequest createDelete(Uri uri) {
        return createRequest(HttpMethods.DELETE, uri);
    }

    private HttpRequest createGet(Uri uri) {
        return createRequest(HttpMethods.GET, uri);
    }

    private HttpRequest createRequest(HttpMethod method, Uri uri, String json) {
        return HttpRequest.create()
            .withMethod(method)
            .withEntity(ContentTypes.APPLICATION_JSON, json)
            .withUri(uri);
    }

    private HttpRequest createRequest(HttpMethod method, Uri uri) {
        return HttpRequest.create()
                .withMethod(method)
                .withUri(uri);
    }

    private List<Pair<Long, CompletionStage<JsonElement>>> extractJson(List<Pair<Long, CompletionStage<HttpResponse>>> responses) {
        return responses.stream().map(pair -> {
            CompletionStage<JsonElement> json = pair.second().thenCompose(this::extractJson);
            return pair.copy(pair.first(), json);
        }).collect(Collectors.toList());
    }

    private CompletionStage<JsonElement> extractJson(HttpResponse response) {
        CompletionStage<JsonElement> stage;
        if (response.status() == StatusCodes.CREATED) {
            stage = response.entity()
                    .getDataBytes()
                    .runFold(ByteString.empty(), ByteString::concat, this.materializer)
                    .thenApply(byteString -> parser.parse(byteString.utf8String()));
        } else {
            CompletableFuture<JsonElement> fut = new CompletableFuture<>();
            fut.completeExceptionally(new Exception("" + response.status() + response.entity().toString()));
            stage = fut;
        }
        return stage;
    }

}
