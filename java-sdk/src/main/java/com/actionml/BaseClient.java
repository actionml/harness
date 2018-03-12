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

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.*;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.http.javadsl.model.headers.Authorization;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import scala.util.Try;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.PasswordAuthentication;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static scala.compat.java8.JFunction.*;

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

        Boolean isHttps = host.startsWith("https");
        if (isHttps) {
            ConnectionPoolSettings settings = ConnectionPoolSettings.create(system);
            try {
                Http http = Http.get(system);
                http.setDefaultClientHttpsContext(httpsContext());
                poolClientFlow = http.cachedHostConnectionPool(
                        ConnectHttp.toHostHttps(host, port),
                        settings,
                        system.log(),
                        materializer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            poolClientFlow = Http.get(system).cachedHostConnectionPool(
                    ConnectHttp.toHost(host, port),
                    materializer);
        }
    }

    private HttpsConnectionContext httpsContext() throws KeyStoreException, IOException, CertificateException,
            NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        String sslConfPath = System.getenv().getOrDefault("HARNESS_SSL_CONFIG_PATH", "./conf/akka-ssl.conf");
        Config config = ConfigFactory.parseFile(new File(sslConfPath));
        ConfigObject keyManagerConfig = config.getObjectList("akka.ssl-config.keyManager.stores").get(0);
        String storeType = (String) keyManagerConfig.get("type").unwrapped();
        String storePath = (String) keyManagerConfig.get("path").unwrapped();
        String storePassword = (String) keyManagerConfig.get("password").unwrapped();

        char[] password = storePassword.toCharArray();

        KeyStore keystore = KeyStore.getInstance(storeType);
        InputStream keystoreFile = new FileInputStream(new File(storePath));

        keystore.load(keystoreFile, password);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keystore, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keystore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return ConnectionContext.https(sslContext);
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

    protected HttpRequest createAccessTokenRequest(PasswordAuthentication credentials) {
        return createRequest(HttpMethods.POST, Uri.create("/auth/token"))
                .withEntity(FormData.create(Pair.create("grant_type", "client_credentials")).toEntity())
                .addHeader(Authorization.basic(credentials.getUserName(), new String(credentials.getPassword())));
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

    protected Source<Optional<String>, NotUsed> withAuth(PasswordAuthentication creds) {
        return Source.single(creds)
                .map(this::createAccessTokenRequest)
                .map(t -> Pair.create(t, 0L))
                .via(this.poolClientFlow)
                .flatMapConcat(p -> p.first().map(func(response ->
                    response.entity().getDataBytes().map(body -> {
                        JsonElement json = toJsonElement(body.decodeString("UTF-8"));
                        AccessTokenResponse tokenResponse = gson.fromJson(json, AccessTokenResponse.class);
                        return Optional.of(tokenResponse.access_token);
                    }))
                ).getOrElse(func(() -> { throw new RuntimeException(); })));
    }

    public JsonElement toJsonElement(String json) {
        return parser.parse(json);
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


    private class AccessTokenResponse {
        String access_token;
    }
}
