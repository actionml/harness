# Java SDK API

 - Supports all Server REST APIs for Events and Queries
 - packages JSON for REST call
 - implements SSL and auth
 - modeled after the PredictionIO Java SDK API where possible
 - written based on [http-akka client](http://doc.akka.io/docs/akka-http/current/java/http/introduction.html#http-client-api)
 - provides synchronous and async type APIs
 - requires Java 8

## Building the Harness Java SDK

Using git pull the source for Harness.

    pull https://github.com/actionml/harness.git harness   
    
Examine `harness/java-sdk/src/main/java/QueryClientExample.java` and `harness/java-sdk/src/main/java/QueryClientExample.java` which have working examples for Contextual Bandit input events and queries.

Copy the source of the Java SDK to your own project starting at `harness/java-sdk/src` and build it and your application with the added dependencies listed in the pom.xml at `harness/java-sdk/pom.xml`

## Sending Events 

Perhaps the most important thing to note about sending events is that the SDKs support asynchronous APIs. This almost always more high performance than blocking an event send to wait for a response before the next send. **However**: This is not compatible with some Engines that require events to be guaranteed to be processed in the order they are sent. For instance the Contextual Bandit must get a new testGroup created before receiving conversion events for the group. Therefore we show how to use the Asynchronous sendEvent in a blocking manner to avoid this problem. Async and sync methods can be mixed on a send by send basis as long as the Engine supports this, for instance the CB needs to have the testGroup creation sent in a synchronous blocking manner but once this has been processed new usage events can be sent asynchronously--check your Engine for it's requirements. Most Lambda Engines see events as streams ordered by timestamps so can operate completely asynchronous, some Kappa Engine need to get ordered events.

### Java 8 Functional Asynchronous Event Send

Using Java 8 functional style conventions an example event sending code looks like this:

    EventClient client = new EventClient(engineId, "localhost", 9090);
        
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


The important bit in creating a client:

    EventClient client = new EventClient(engineId, "localhost", 9090);
    
The engineId must be a string that uniquely ids the server-side engine. 

Use the Event builder for PredictionIO style events:

    Event event = new Event()
        .eventId("ed15537661f2492cab64615096c93160")
        .event("$set")
        ...

Or create your events as JSON when PIO formatting is not desired and send the JSON via:

    event = "{\"some\":\"json\"}"
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


and send the event:

    client.sendEvent(event)
    
### Making the Async Event Send Synchronous

We have only to add a `.get()` and code to catch possible exceptions to make the asynch methods synchronous:

    try {
        // using the .get() forces the code to wait for the response and so is blocking
        Pair<Integer, String> p = 
            ((CompletableFuture<Pair<Integer, String>>) client.sendEvent(event)).get();
        log.info("Sent event: " + event + "\nResponse code: " + p.first().toString());
    } catch (InterruptedException | ExecutionException e) {
        log.error("Error in client.sendEvent waiting for a response ", e);
    }

This uses the `sendEvent(String event)` taking a JSON string as the 
Event definition. 

**Note**: Checking for errors is important since you will receive them for many reasons and they are self-describing. See the [REST Specification](https://github.com/actionml/harness/blob/master/rest_spec.md) for a description of the response codes specifics.

## Sending Queries

Queries are specific to each template so are created directly from JSON strings:

    String engineId = "test_resource";
    QueryClient client = new QueryClient(engineId, "localhost", 8080);
    
    String query = "{\"user\": \"user-1\",\"groupId\": \"group-1\"}";
    
    try {
        System.out.println("Send query: " + query);
        long start = System.currentTimeMillis();
        client.sendQuery(query).whenComplete((queryResult, throwable) -> {
            long duration = System.currentTimeMillis() - start;
            if (throwable == null) {
                System.out.println("Receive eventIds: " + 
                    queryResult.toString() + ", " + duration + " ms.");
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

Since Query formats are always defined by the specific Engine there is no builder for them, they must be sent as raw JSON. The code to process a specific response must be in the Java Promise that is after the `.whenCompleted` or using the `.get()` method similar to the `sendEvent` function, the query can be done synchronously.

# Security

Read no further if you are not using TLS/SSL and Authentication.

When using TLS/SSL or when Harness is running with authentication required, setup as shown below. This is not necessary with connection level security, or where the network environment does not require extra security.

Not also that you cannot have a secure deployment without both TLS and Auth. TLS allows Harness to be trusted by the client and Auth allows the client to be trusted by Harness. Using both it is possible to connect from a mobile device or browser directly to an instance of Harness.

## Setup TLS/SSL

The Java SDK works with or without TLS. In either case a `.pem` certificate must be provided, even if it is not used. The cert file is used too encrypt data to a specific Harness server, which has the correct key installed. See [harness-config.md](harness-config.md) for a description of how to setup the Harness Server and Python CLI for use with TLS/SSL.

The Java SDK has 3 methods for supplying the `.pem` file:

 1. **Change the config** in `akka-ssl.conf` to point to the `.pem` file on the desired machine and recompile the client for use on a specific machine.
 - **Add an environment variable** by putting `export HARNESS_SERVER_CERT_PATH=/path/to/pem-file` in one of the shell startup file like `~/.profile` or `~/.bashrc` as per recommendations for your OS.
 - **Pass in the location** when creating any client. All clients have an optional parameter for setting the path to the `.pem` file.

The method falls back from #3 to #1 and if a `.pem` file is not found there will always be an exception thrown.

Harness is built with a `.pem` and `.jks` file for localhost execution of TLS with pointers to these files setup in the correct places. By default Harness does not use TLS but to try TLS with localhost all you have to do is turn it on in `harness/rest-server/bin/harness-env` and start Harness. The CLI will use TLS to communicate with Harness on localhost and the Java SDK tests will work. 

To use a custom certificate with the Java SDK, just make sure it can find the `.pem` at runtime.
    
## Using Auth from the Client

When Harness is running in "Authentication Required" mode a **User** and **Secret** must have been created on the Harness Server using the CLI or REST interface. When Harness is first started it is in non-TLS mode, with no need of an admin user, so create one before turning on authentication and make a note of the user-id and secret needed. The User must have **Permission** to access the resource/engineId used in the client code examples above and must have the role **Client** or **Admin**. See the [CLI docs](commands.md) for more details

  