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
import akka.http.javadsl.model.headers.Authorization;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.sslconfig.akka.AkkaSSLConfig;
import com.typesafe.sslconfig.ssl.TrustManagerFactoryWrapper;
import scala.util.Try;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static scala.compat.java8.JFunction.func;

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
    protected final Optional<Path> optionalServerCertPath;

    public BaseClient(String host, Integer port, Optional<Path> optionalServerCertPath) {
        this.optionalServerCertPath = optionalServerCertPath;
        system = ActorSystem.create("actionml-sdk-client");
        Function<Throwable, Supervision.Directive> decider = e -> {
            system.log().error(e, "Supervision error");
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

    private HttpsConnectionContext httpsContext() throws NoSuchAlgorithmException, KeyManagementException,
            InvalidAlgorithmParameterException, CertificateException, IOException {
        AkkaSSLConfig akkaSslConfig = AkkaSSLConfig.get(system);
        Path certPath = optionalServerCertPath.orElseGet(() -> {
            return akkaSslConfig.config()
                    .trustManagerConfig()
                    .trustStoreConfigs()
                    .headOption()
                    .flatMap(func(c -> c.filePath().map(func(URI::create))))
                    .getOrElse(func(() -> {
                        Map<String, String> env = System.getenv();
                        String result = null;
                        if (env.containsKey("HARNESS_SERVER_CERT_PATH")) {
                            result = env.get("HARNESS_SERVER_CERT_PATH");
                        } else {
                            URL fallbackCert = ClassLoader.getSystemResource("harness.pem");
                            if (fallbackCert == null) throw new RuntimeException("Wrong TLS config. Server certificate is not provided.");
                            result = fallbackCert.getPath();
                        }
                        return Paths.get(result);
                    }));
        });
        byte[] certContent = Files.lines(certPath)
                .filter(l -> !l.equals("-----BEGIN CERTIFICATE-----") && !l.equals("-----END CERTIFICATE-----"))
                .reduce((acc, l) -> acc + l)
                .map(l -> Base64.getDecoder().decode(l)).orElseThrow(() -> new RuntimeException("Wrong PEM format or empty certificate."));

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certContent));
        TrustManagerFactoryWrapper tmf = akkaSslConfig.buildTrustManagerFactory(akkaSslConfig.config());
        Set<TrustAnchor> trustAnchors = new HashSet() {{ add(new TrustAnchor(cert, null)); }};
        PKIXBuilderParameters certParams = new PKIXBuilderParameters(trustAnchors, new X509CertSelector());
        tmf.init(new CertPathTrustManagerParameters(certParams));
        TrustManager[] trustManagers = tmf.getTrustManagers();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());
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
