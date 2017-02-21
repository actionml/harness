package com.actionml;

import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.BasicHttpCredentials;
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

    AuthClient(String host, Integer port, String clientId, String clientSecret) {
        super(host, port);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public CompletionStage<JsonElement> getAccessToken(Uri uri) {

        Authorization authorization = Authorization.basic(clientId, clientSecret);
        HttpRequest request = HttpRequest.create()
                .addHeader(authorization)
                .withMethod(HttpMethods.POST)
                .withEntity(FormData.create(ImmutableMap.of("grant_type", "client_credentials")).toEntity())
                .withUri(uri);

        return this.single(request).thenCompose(this::extractJson);
    }

    private CompletionStage<JsonElement> extractJson(HttpResponse response) {
        CompletionStage<JsonElement> stage;
        if (response.status() == StatusCodes.OK) {
            stage = response.entity()
                    .getDataBytes()
                    .runFold(ByteString.empty(), ByteString::concat, this.materializer)
                    .thenApply(byteString -> parser.parse(byteString.utf8String()));
        } else {
            CompletableFuture<JsonElement> fut = new CompletableFuture<>();
            fut.completeExceptionally(new Exception("Error: " + response.status() + " " + response.entity().toString()));
            stage = fut;
        }
        return stage;
    }

}
