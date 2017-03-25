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

import com.actionml.EventClient;
import com.actionml.entity.Event;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 18:46
 */
public class EventClientExample {

    static Logger log = LoggerFactory.getLogger(EventClientExample.class);

    public static void main(String[] args) {

        String datasetId = "DATASET-ID";
        EventClient client = new EventClient(datasetId, "localhost", 8080);


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


//        String fileName = "data/events.log";
//
//        try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {
//
//            List<Event> events = br.lines().limit(10)
//                    .map(client::toJsonElement)
//                    .map(jsonElement -> client.toPojo(jsonElement, Event.class))
//                    .collect(Collectors.toList());
//
//            System.out.println("Send events: " + events.size());
//
//            long start = System.currentTimeMillis();
//            client.createEvents(events).whenComplete((eventIds, throwable) -> {
//                long duration = System.currentTimeMillis() - start;
//                if (throwable == null) {
//                    System.out.println("Receive eventIds: " + eventIds.size() + ", " + duration + " ms. " + (eventIds.size() / (duration / 1000)) + " per second");
//                } else {
//                    System.err.println(throwable.getMessage());
//                }
//                client.close();
//            });
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

}
