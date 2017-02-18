package com.actionml;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ConnectionPoolSettings;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         05.02.17 14:21
 */
abstract class BaseClient {

    private final ActorSystem system;
    private final Materializer materializer;
    private final String host;
    private final Integer port;
    private final Flow<Pair<HttpRequest, Long>, Pair<Try<HttpResponse>, Long>, HostConnectionPool> poolClientFlow;
    final JsonParser parser = new JsonParser();
    final GsonBuilder gsonBuilder = new GsonBuilder();
    {
        gsonBuilder.registerTypeAdapter(org.joda.time.DateTime.class, new DateTimeAdapter());
    }
    final Gson gson = gsonBuilder.create();

    BaseClient(String host, Integer port) {
        system = ActorSystem.create("pio-kappa-sdk-client");
        Function<Throwable, Supervision.Directive> decider = exc -> {
            System.err.println(exc);
            return Supervision.resume();
        };
        materializer = ActorMaterializer.create(
                ActorMaterializerSettings.create(system).withSupervisionStrategy(decider),
                system);

        this.host = host;
        this.port = port;

        ConnectionPoolSettings settings = ConnectionPoolSettings.create(system);
        poolClientFlow = Http.get(system).cachedHostConnectionPool(
                ConnectHttp.toHostHttps(host, port),
                settings,
                system.log(),
                materializer);
    }

    public Materializer getMaterializer() {
        return materializer;
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

    public CompletionStage<List<Pair<Long, CompletionStage<JsonElement>>>> multiPost(Uri uri, List<String> jsonList) {
        List<HttpRequest> requestList = jsonList.stream()
                .map(json -> createPost(uri, json))
                .collect(Collectors.toList());
        return multi(requestList).thenApply(this::extractJson);
    }

    public CompletionStage<JsonElement> delete(Uri uri) {
        return single(createDelete(uri)).thenCompose(this::extractJson);
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

    private CompletionStage<List<Pair<Long, CompletionStage<HttpResponse>>>> multi(List<HttpRequest> requests) {
        return Source.from(requests).zipWithIndex()
            .map(pair -> pair.copy(pair.first(), (Long) pair.second()))
            .via(poolClientFlow)
            .runFold(new ArrayList<>(), (storage, pair) -> {
                CompletableFuture<HttpResponse> future = new CompletableFuture<>();
                Long index = pair.second();
                Try<HttpResponse> tryResponse = pair.first();
                if (tryResponse.isSuccess()) {
                    future.complete(tryResponse.get());
                } else {
                    future.completeExceptionally(tryResponse.failed().get());
                }
                storage.add(Pair.create(index, future));
                return storage;
            }, materializer);
    }

    private CompletionStage<HttpResponse> single(HttpRequest request) {
        return Source.single(Pair.create(request, 0L))
                .via(poolClientFlow)
                .runWith(Sink.head(), materializer)
                .thenCompose(pair -> {
                    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
                    Try<HttpResponse> tryResponse = pair.first();
                    if (tryResponse.isSuccess()) {
                        future.complete(tryResponse.get());
                    } else {
                        future.completeExceptionally(tryResponse.failed().get());
                    }
                    return future;
                });
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

    protected <T> T toPojo(JsonElement jsonElement, Class<T> classOfT) throws JsonSyntaxException {
        return gson.fromJson(jsonElement, classOfT);
    }

    void close() {
        System.out.println("Shutting down client");
        Http.get(system).shutdownAllConnectionPools().whenComplete((s, f) -> system.terminate());
    }

}
