package com.actionml;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
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
import com.google.gson.*;
import scala.util.Try;

import java.util.ArrayList;
import java.util.List;
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

    protected CompletionStage<List<Pair<Long, CompletionStage<HttpResponse>>>> multi(List<HttpRequest> requests) {
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

    protected CompletionStage<HttpResponse> single(HttpRequest request) {
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

    protected <T> T toPojo(JsonElement jsonElement, Class<T> classOfT) throws JsonSyntaxException {
        return gson.fromJson(jsonElement, classOfT);
    }

    public Materializer getMaterializer() {
        return materializer;
    }

    void close() {
        System.out.println("Shutting down client");
        Http.get(system).shutdownAllConnectionPools().whenComplete((s, f) -> system.terminate());
    }
}
