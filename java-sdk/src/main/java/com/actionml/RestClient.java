package com.actionml;

import akka.http.javadsl.model.*;
import akka.japi.Pair;
import akka.util.ByteString;
import com.google.gson.JsonElement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    public CompletionStage<JsonElement> put(Uri uri, String json) {
        return single(createPut(uri, json)).thenCompose(this::extractJson);
    }

    public CompletionStage<JsonElement> post(Uri uri, String json) {
        return single(createPost(uri, json)).thenCompose(this::extractJson);
    }

    public CompletionStage<JsonElement> post(Uri uri, String id, String json) {
        return post(uri.addPathSegment(id), json);
    }

    public CompletionStage<JsonElement> delete(Uri uri, String id) {
        return single(createDelete(uri.addPathSegment(id))).thenCompose(this::extractJson);
    }

    protected HttpRequest createPost(Uri uri, String json) {
        return createRequest(HttpMethods.POST, uri, json);
    }

    protected HttpRequest createPut(Uri uri, String json) {
        return createRequest(HttpMethods.PUT, uri, json);
    }

    protected HttpRequest createDelete(Uri uri) {
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

    protected CompletionStage<Pair<Long, JsonElement>> extractJson(Pair<Long, HttpResponse> pair) {
        return extractJson(pair.second()).thenApply(jsonElement -> Pair.create(pair.first(), jsonElement));
    }

    protected CompletionStage<JsonElement> extractJson(HttpResponse response) {
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
