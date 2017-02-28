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

    // Resource location
    protected final Uri uri;

    RestClient(String host, Integer port, Uri uri) {
        super(host, port);
        this.uri = uri;
    }

    /**
     * Get exist resource
     *
     * @param id ID Resource
     * @return Resource as JsonElement
     */
    public CompletionStage<JsonElement> get(String id) {
        return single(createGet(id)).thenCompose(this::extractJson);
    }

    /**
     * Create new resource
     *
     * @return ID resource
     */
    public CompletionStage<JsonElement> create() {
        return single(createPost("{}")).thenCompose(this::extractJson);
    }

    /**
     * Create new resource
     *
     * @param json Resource as json string
     * @return ID resource
     */
    public CompletionStage<JsonElement> create(String json) {
        return single(createPost(json)).thenCompose(this::extractJson);
    }

    /**
     * Create new resource with preset ID
     *
     * @param json Resource as json string
     * @return ID resource
     */
    public CompletionStage<JsonElement> create(String id, String json) {
        return single(createPost(id, json)).thenCompose(this::extractJson);
    }

    /**
     * Update exist resource
     *
     * @param id ID Resource
     * @param json Resource as json
     * @return ID Resource
     */
    public CompletionStage<JsonElement> update(String id, String json) {
        return single(createPost(id, json)).thenCompose(this::extractJson);
    }

    /**
     * Remove exist resource
     *
     * @param id ID Resource
     * @return ID Resource
     */
    public CompletionStage<JsonElement> delete(String id) {
        return single(createDelete(id)).thenCompose(this::extractJson);
    }

    protected HttpRequest createGet(String id) {
        return createGet(uri.addPathSegment(id));
    }

    protected HttpRequest createPost(String id, String json) {
        return createPost(uri.addPathSegment(id), json);
    }

    protected HttpRequest createPost(String json) {
        return createPost(uri, json);
    }

    protected HttpRequest createDelete(String id) {
        return createDelete(uri.addPathSegment(id));
    }

    protected CompletionStage<Pair<Long, JsonElement>> extractJson(Pair<Long, HttpResponse> pair) {
        return extractJson(pair.second()).thenApply(jsonElement -> Pair.create(pair.first(), jsonElement));
    }

}
