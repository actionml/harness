package com.actionml;

import akka.http.javadsl.model.Uri;
import com.actionml.entity.Query;
import com.actionml.entity.QueryResult;
import com.google.gson.JsonElement;

import java.util.concurrent.CompletionStage;

/**
 * Created by semen on 01.03.17.
 */
public class QueryClient extends RestClient {

    public QueryClient(String engineId, String host, Integer port) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("queries"));
    }

    public CompletionStage<QueryResult> sendQuery(Query query) {
        return this.create(query.toJsonString()).thenApply(this::toQueryResult);
    }

    protected QueryResult toQueryResult(JsonElement jsonElement) {
        return toPojo(jsonElement, QueryResult.class);
    }

}
