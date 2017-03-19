# Java SDK for PIO-Kappa

The Java client can be build with the following additions to a Maven POM/xml. 

**Note**: These locations and artifact ids are preliminary, check with the ActionML before using!

```
<dependency>
    <groupId>com.actionml</groupId>
    <artifactId>sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Include the Java artifacts from their location on github:

```
<repositories>
    <repository>
        <id>PIO-Kappa</id>
        <url>https://raw.github.com/actionml/PIO-Kappa/mvn-repo/</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>
```


# Example Client Code for Input

To create an example event and send it:

    POST: /dataset/resource-id/events
    
To the following does the POST as well as sending the event to the PIO-Kappa server:

    
```
public class EventClientExample {

    static Logger log = LoggerFactory.getLogger(EventClientExample.class);

    public static void main(String[] args) {

        String datasetId = "resource-id";// must be legal URI fragment
        EventClient client = new EventClient(datasetId, "localhost", 8080);

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
    }

}
```

Queries require building a JSON string and parsing the response since each template defines its own query and query result format and data.

```
String query = "\"user\"\: \"user-1\"\,\"groupId\"\: \"group-1\"");
client.sendQuery(query).whenComplete(queryResult, throwable) -> {
    System.out.println("Received JSON result: " + queryResult) 
    //query result is a JSON string
}
```

