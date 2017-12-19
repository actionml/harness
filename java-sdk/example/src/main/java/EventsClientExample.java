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

import akka.japi.Pair;
import com.actionml.EventsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 18:46
 */
public class EventsClientExample {

    private static Logger log = LoggerFactory.getLogger(EventsClientExample.class);

    public static void main(String[] args) {

        String serverHost = args[0]; // this should be https://... for TLS/SSL when Harness has TLS/SSL enabled
        String engineId = args[1];
        String fileName = args[2];
        Integer serverPort = 9090;

        log.info("Args: {}, {}, {}, {}", engineId, fileName, serverHost, serverPort);

        Map<String, String> env = System.getenv();
        Optional<String> optUsername = Optional.ofNullable(env.getOrDefault("HARNESS_CLIENT_USER_ID", null));
        Optional<String> optPassword = Optional.ofNullable(env.getOrDefault("HARNESS_CLIENT_USER_SECRET", null));
        Optional<PasswordAuthentication> optionalCreds = optUsername.flatMap(username -> optPassword.map( password ->
                new PasswordAuthentication(username, password.toCharArray())
        ));

        EventsClient client = new EventsClient(engineId, serverHost, serverPort, optionalCreds);

        // example of JSON for creating an event
        String json = "{" +
                "\"eventId\":\"ed15537661f2492cab64615096c93160\"," +
                "\"event\":\"$set\"," +
                "\"entityType\":\"testGroup\"," +
                "\"entityId\":\"9\"," +
                "\"properties\":{" +
                    "\"testPeriodStart\":\"2016-07-12T00:00:00.000+09:00\"," +
                    "\"pageVariants\":[\"17\",\"18\"]," +
                    "\"testPeriodEnd\":\"2016-08-31T00:00:00.000+09:00\"}," +
                "\"eventTime\":\"2016-07-12T16:08:49.677+09:00\"," +
                "\"creationTime\":\"2016-07-12T07:09:58.273Z\"}";

        /* This example creates an event from constants
        Event event = new Event()
                .eventId("ed15537661f2492cab64615096c93160")
                .event("$set")
                .entityType("testGroup")
                .entityId("9")
                .properties(ImmutableMap.of(
                        "testPeriodStart", "2016-07-12T00:00:00.000+09:00",
                        "testPeriodEnd", "2016-08-31T00:00:00.000+09:00",
                        "pageVariants", ImmutableList.of("17", "18")
                ))
                .eventTime(new DateTime("2016-07-12T16:08:49.677+09:00"))
                .creationTime(new DateTime("2016-07-12T07:09:58.273Z"));


        log.info("Send event {}", event.toJsonString());
        client.sendEvent(event).whenComplete((response, throwable) -> {
            log.info("Response: {}", response);
            if (throwable != null) {
                log.error("Create event error", throwable);
            }
            client.close();
        });
        */

        // This examples takes a file of JSON, one object per line and sends them all asynchronously to the server.
        // Async sends are faster but can only be used if the Engine does not care about the order of Event processing
        // so use with care, not to be used with the Contextual Bandit for example.
        /*
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {

            List<String> events = br.lines().collect(Collectors.toList());

            log.info("Send events: " + events.size());

            long start = System.currentTimeMillis();
            client.createEvents(events).thenApply(pairs -> {
                long duration = System.currentTimeMillis() - start;
                Map<String, Long> counting = pairs.stream()
                        .map(p -> p.second().first().toString())
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                log.info("Responses: " + pairs.size());
                log.info("Finished: " + duration);
                counting.forEach((code, cnt) -> log.info("Status " + code + ": " + cnt));
                return pairs.size();
            }).whenComplete((size, throwable) -> {
                log.info("Complete: " + size);
                log.info("Close client");
                client.close();
            });

        } catch (IOException e) {
            log.error("Oops, we have an error: ", e);
            client.close();
        }
        */

        // This example reads one event and sends it synchronously to the server, waiting for a response after each send
        // This is slower than using async sendEvent but is required for Engines that care about the order of processing
        // so for instance use this method with the Contextual Bandit.
        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {

            List<String> events = br.lines().collect(Collectors.toList());

            log.info("Number of events to send: " + events.size());
            long start = System.currentTimeMillis();

            for ( String event: events) {
                try {
                    // using the .get() forces the code to wait for the response and so id blocking
                    // as an alternative use "client.sendEventSync", which may throw the same exceptions listed below
                    Pair<Integer, String> p = ((CompletableFuture<Pair<Integer, String>>) client.sendEvent(event)).get();
                    log.info("Sent event: " + event + "\nResponse code: " + p.first().toString());
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error in client.sendEvent waiting for a response ", e);
                }
            }

            log.info("Close client");
            client.close();
            long duration = System.currentTimeMillis() - start;
            log.info("Finished queuing send of " + events.size() + " events in : " + duration + " milliseconds");

        } catch (IOException e) {
            log.error("Oops, we have an unrecoverable error: ", e);
            client.close();
        }

    }

}
