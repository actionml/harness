///*
// * Copyright ActionML, LLC under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * ActionML licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.actionml;
//
//import akka.http.javadsl.model.FormData;
//import akka.http.javadsl.model.HttpMethods;
//import akka.http.javadsl.model.HttpRequest;
//import akka.http.javadsl.model.Uri;
//import akka.http.javadsl.model.headers.Authorization;
//import com.google.common.collect.ImmutableMap;
//import com.google.gson.JsonElement;
//
//import java.util.concurrent.CompletionStage;
//
///**
// * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
// *         20.02.17 20:08
// */
//public class AuthClient extends BaseClient {
//
//    private final String clientId;
//    private final String clientSecret;
//    private final Uri uri = Uri.create("/oauth");
//
//    public AuthClient(String host, Integer port, String clientId, String clientSecret) {
//        super(host, port);
//        this.clientId = clientId;
//        this.clientSecret = clientSecret;
//    }
//
//    public CompletionStage<JsonElement> getAccessToken() {
//
//        Authorization authorization = Authorization.basic(clientId, clientSecret);
//        HttpRequest request = HttpRequest.create()
//                .addHeader(authorization)
//                .withMethod(HttpMethods.POST)
//                .withEntity(FormData.create(ImmutableMap.of("grant_type", "client_credentials")).toEntity())
//                .withUri(uri.addPathSegment("access_token"));
//
//        return this.single(request).thenCompose(this::extractResponse);
//    }
//
//    public CompletionStage<JsonElement> refreshToken(String refreshToken) {
//
//        Authorization authorization = Authorization.basic(clientId, clientSecret);
//        HttpRequest request = HttpRequest.create()
//                .addHeader(authorization)
//                .withMethod(HttpMethods.POST)
//                .withEntity(FormData.create(ImmutableMap.of(
//                        "grant_type", "client_credentials",
//                        "refresh_token", refreshToken
//                )).toEntity())
//                .withUri(uri.addPathSegment("refresh_token"));
//
//        return this.single(request).thenCompose(this::extractResponse);
//    }
//
//}
