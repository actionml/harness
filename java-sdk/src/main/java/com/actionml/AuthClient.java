package com.actionml;

import akka.http.javadsl.model.*;
import akka.http.javadsl.model.FormData;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
import akka.http.scaladsl.model.*;
import akka.util.ByteString;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.sun.deploy.net.protocol.ProtocolType.HTTP;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         20.02.17 20:08
 */
public class AuthClient extends BaseClient {

    private final String clientId;
    private final String clientSecret;
    private final Uri uri = Uri.create("/oauth");

    public AuthClient(String host, Integer port, String clientId, String clientSecret) {
        super(host, port);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public CompletionStage<JsonElement> getAccessToken() {

        Authorization authorization = Authorization.basic(clientId, clientSecret);
        HttpRequest request = HttpRequest.create()
                .addHeader(authorization)
                .withMethod(HttpMethods.POST)
                .withEntity(FormData.create(ImmutableMap.of("grant_type", "client_credentials")).toEntity())
                .withUri(uri.addPathSegment("access_token"));

        return this.single(request).thenCompose(this::extractJson);
    }

    public CompletionStage<JsonElement> refreshToken(String refreshToken) {

        Authorization authorization = Authorization.basic(clientId, clientSecret);
        HttpRequest request = HttpRequest.create()
                .addHeader(authorization)
                .withMethod(HttpMethods.POST)
                .withEntity(FormData.create(ImmutableMap.of(
                        "grant_type", "client_credentials",
                        "refresh_token", refreshToken
                )).toEntity())
                .withUri(uri.addPathSegment("refresh_token"));

        return this.single(request).thenCompose(this::extractJson);
    }

}
