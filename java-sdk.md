# Java SDK API

 - Supports all Server REST for Input and Query
 - packages JSON for REST call
 - implements SSL and auth
 - modeled after the PredictionIO Java SDK API where possible
 - written based on [http-akka client](http://doc.akka.io/docs/akka-http/current/java/http/introduction.html#http-client-api)

## Building the pio-kappa Java SDK

Using git pull the source for pio-kappa. This includes the Java SDK, the Rest-server, and the contextual bandit template. When we are done the SDK will be a separate artifact that can be linked into a Java project, for now follow these steps:

    pull https://github.com/actionml/pio-kappa.git pio-kappa

make sure to get the current `develop` branch

    cd pio-kappa
    git checkout -b develop
    git pull origin develop    
    
Examine `pio-kappa/java-sdk/src/main/java/QueryClientExample.java` and `pio-kappa/java-sdk/src/main/java/QueryClientExample.java` which have working examples for Contextual Bandit input events and queries.

Copy the source of the Java SDK to your own project starting at `pio-kappa/java-sdk/src` and build it and your application with the added dependencies listed in the pom.xml at `pio-kappa/java-sdk/pom.xml`

## Sending Events 

Sending Events uses a new style but essentially creates the same json. However it communicates with the pio-kappa server using REST in a rather different manner than Apache PIO. For input you would identify the dataset you want events to go into using a REST resource-id. Currently this is ignored so only one dataset is allowed and the resource-id is only a placeholder. It will be used to identify the correct dataset as we add methods for enabling them.

Using Java 8 functional style conventions an example event sending code looks like this:

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


The important bit here is creating a client:

    EventClient client = new EventClient(datasetId, "localhost", 8080);
    
The datasetId must be a string the uniquely ids the server-side dataset. The id is ignored currently so all data goes into the single dataset. The host address must be the remote pio-kappa server. The port can be changed but is 8080 by default.

Use the Event builder:

    Event event = new Event()
        .eventId("ed15537661f2492cab64615096c93160")
        .event("$set")
        ...

and send the event:

    client.sendEvent(event)

**Note**: Checking for errors if extremely important for group initialization events. If these are not received correctly no further input will be processed for the group.

## Sending Queries

Queries are specific to each template so are created directly from JSON strings:

    String engineId = "test-resource";
    QueryClient client = new QueryClient(engineId, "localhost", 8080);
    
    String query = "{\"user\": \"user-1\",\"groupId\": \"group-1\"}";
    
    try {
        System.out.println("Send query: " + query);
        long start = System.currentTimeMillis();
        client.sendQuery(query).whenComplete((queryResult, throwable) -> {
            long duration = System.currentTimeMillis() - start;
            if (throwable == null) {
                System.out.println("Receive eventIds: " + queryResult.toString() + ", " + duration + " ms.");
            } else {
                System.err.println(throwable.getMessage());
            }
            client.close();
        });
    
    } catch (Exception e) {
        e.printStackTrace();
    }

First create the `QueryClient`

    QueryClient client = new QueryClient(engineId, "localhost", 8080);

Then send the JSON string to the query endpoint for the correct engine

    client.sendQuery(query)

## Apache PredictionIO-0.10.0 Java Client Input and Query SDK (For comparison only)

The PIO 0.10.0 client is [here](https://github.com/apache/incubator-predictionio-sdk-java).

The old style Java SDK has 2 clients, [one for input](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EventClient.java) and [one for queries](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java), The PIO-Kappa SDK will have one client for all APIs deriving resource endpoints from resource-ids and the PIO-Kappa server address.

### Input

The `Event` class should be instantiated in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/Event.java) A new route should be created for input derived from the new REST server address:port and the new `datasets` resource-id.

### Query

A query Map should be converted into a JSON payload in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java#L93) as the old SDK. A new route will be derived from the PIO-Kappa Server address:port and the `engines` resource-id.
