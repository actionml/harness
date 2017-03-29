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

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import com.google.gson.JsonElement;

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
    public CompletionStage<Pair<Integer, String>> get(String id) {
        return single(createGet(id)).thenCompose(this::extractResponse);
    }

    /**
     * Create new resource
     *
     * @return ID resource
     */
    public CompletionStage<Pair<Integer, String>> create() {
        return single(createPost("{}")).thenCompose(this::extractResponse);
    }

    /**
     * Create new resource
     *
     * @param json Resource as json string
     * @return ID resource
     */
    public CompletionStage<Pair<Integer, String>> create(String json) {
        return single(createPost(json)).thenCompose(this::extractResponse);
    }

    /**
     * Create new resource with preset ID
     *
     * @param json Resource as json string
     * @return ID resource
     */
    public CompletionStage<Pair<Integer, String>> create(String id, String json) {
        return single(createPost(id, json)).thenCompose(this::extractResponse);
    }

    /**
     * Update exist resource
     *
     * @param id ID Resource
     * @param json Resource as json
     * @return ID Resource
     */
    public CompletionStage<Pair<Integer, String>> update(String id, String json) {
        return single(createPost(id, json)).thenCompose(this::extractResponse);
    }

    /**
     * Remove exist resource
     *
     * @param id ID Resource
     * @return ID Resource
     */
    public CompletionStage<Pair<Integer, String>> delete(String id) {
        return single(createDelete(id)).thenCompose(this::extractResponse);
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

    protected CompletionStage<Pair<Long, Pair<Integer, String>>> extractResponses(Pair<Long, HttpResponse> pair) {
        return extractResponse(pair.second()).thenApply(response -> Pair.create(pair.first(), response));
    }

}
