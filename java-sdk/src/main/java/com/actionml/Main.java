package com.actionml;

import akka.http.javadsl.model.Uri;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import com.actionml.entity.Event;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 18:46
 */
public class Main {

    public static void main(String[] args) {

        Event event = new Event()
            .event("$set")
            .entityType("user")
            .entityId("ID");

        List<Event> events = new ArrayList<>();
        List<String> eventIds = new ArrayList<>();

        for (int item = 1; item <= 50; item++) {
            events.add(new Event()
                    .event("event-" + item)
                    .entityType("user")
                    .entityId("ID-" + item));
            eventIds.add("event-" + item);
        }

//        EventClient client = new EventClient("WgfEu", "localhost", 8080);

        AuthClient authClient = new AuthClient("localhost", 8080, "test_client_id", "test_client_secret");

        authClient.getAccessToken(Uri.create("/oauth").addPathSegment("access_token")).whenComplete((json, ex) -> {
            System.out.println("JSON: " + json);
            System.err.println(ex.getMessage());
        });

        Map<String, Object> emptyProperty = ImmutableMap
                .of("size", "S", "color", "Red");

//        client.setUser("user-1", emptyProperty).whenComplete((eventId, ex) -> {
//            System.err.println(ex.getMessage());
//            System.out.println(eventId);
//        });

//        Source.from(events)
//                .via(Flow.of(Event.class).mapAsync(1, client::createEvent))
//        .runForeach(eventId -> {
//            System.out.println(eventId);
//        }, client.getMaterializer());

//        client.createEvent(event).whenComplete((eventId, ex) -> {
//            System.err.println(ex.getMessage());
//            System.out.println(eventId);
//        });
//
//        client.createEvents(events).whenComplete((pairList, ex) -> {
//            System.err.println(ex.getMessage());
//            System.out.println(pairList);
//        });
//
//        client.getEvent("dvhdsovisd89").whenComplete((eventId, ex) -> {
//            System.err.println(ex.getMessage());
//            System.out.println(eventId);
//        });
//
//        client.getEvents(eventIds).whenComplete((eventId, ex) -> {
//            System.err.println(ex.getMessage());
//            System.out.println(eventId);
//        });


    }

}
