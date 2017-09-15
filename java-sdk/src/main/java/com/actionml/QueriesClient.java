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

import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;

import java.net.PasswordAuthentication;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Created by semen on 01.03.17.
 */
public class QueriesClient extends RestClient {

    public QueriesClient(String engineId, String host, Integer port) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("queries"), Optional.empty());
    }

    public QueriesClient(String engineId, String host, Integer port, Optional<PasswordAuthentication> credentials) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("queries"), credentials);
    }

    public CompletionStage<Pair<Integer, String>> sendQuery(String query) {
        return withAuth().toMat(Sink.head(), Keep.right()).run(this.materializer)
                .thenCompose(optionalToken -> this.create(query, optionalToken));
    }

}
