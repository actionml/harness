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
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.actionml.entity.Event;

import java.net.PasswordAuthentication;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 17:50
 */
public class EventsClient extends RestClient {

    public EventsClient(String engineId, String host, Integer port) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("events"), Optional.empty(), Optional.empty());
    }

    public EventsClient(String engineId, String host, Integer port, Optional<PasswordAuthentication> optionalCreds) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("events"), optionalCreds, Optional.empty());
    }

    public EventsClient(String engineId, String host, Integer port, Optional<PasswordAuthentication> optionalCreds, Optional<Path> optionalServerCertPath) {
        super(host, port, Uri.create("/engines").addPathSegment(engineId).addPathSegment("events"), optionalCreds, optionalServerCertPath);
    }

    /**
     * Get exist Event
     *
     * @param eventId ID event
     * @return Event
     */
    public CompletionStage<Pair<Integer, String>> getEvent(String eventId) {
        return withAuth().thenCompose(optionalToken -> this.get(eventId, optionalToken));
    }

    public CompletionStage<Pair<Integer, String>> sendEvent(String event) {
        return withAuth().thenCompose(optionalToken -> this.create(event, optionalToken));
    }

    public CompletionStage<Pair<Integer, String>> sendEvent(Event event) {
        return this.sendEvent(event.toJsonString());
    }

    public CompletionStage<List<Pair<Long, Pair<Integer, String>>>> createEvents(List<String> events) {
        return withAuth().thenCompose(optionalToken ->
                        Source.from(events)
                                .map(event -> this.createPost(event, optionalToken))
                                .zipWithIndex()
                                .map(pair -> pair.copy(pair.first(), (Long) pair.second()))
                                .via(this.poolClientFlow)
                                .mapAsync(1, this::extractResponse)
                                .mapAsync(1, this::extractResponses)
                                .runFold(new ArrayList<>(), (acc, pair) -> {
                                    acc.add(pair);
                                    return acc;
                                }, this.materializer)
                );
    }

}
