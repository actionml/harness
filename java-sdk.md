# Java SDK API

 - Supports all Server REST for Input and Query
 - packages JSON for REST call
 - implements SSL and auth
 - modeled after the PredictionIO Java SDK API where possible
 - written based on [http-akka client](http://doc.akka.io/docs/akka-http/current/java/http/introduction.html#http-client-api)

## Building the pio-kappa Java SDK

Using git pull the source for pio-kappa. This includes the Java SDK, the Rest-server, and the contextual bandit template. When we are done the SDK will be a separate artifact that can be linked into a Java project, for now follow these steps:

    pull https://github.com/actionml/pio-kappa.git pio-kappa
    
Examine `pio-kappa/java-sdk/src/main/java/QueryClientExample.java` and `pio-kappa/java-sdk/src/main/java/QueryClientExample.java` which have working examples for Contextual Bandit input events and queries.

Copy the source of the Java SDK to your own project starting at `pio-kappa/java-sdk/src` and build it and your application with the added dependencies listed in the pom.xml at `pio-kappa/java-sdk/pom.xml`

## Sending Events 

Sending Events uses a new style but essentially creates the same json. However it communicates with the pio-kappa server using REST in a rather different manner than Apache PIO. For input you would identify the dataset you want events to go into using a REST resource-id. Currently this is ignored so only one dataset is allowed and the resource-id is only a placeholder. It will be used to identify the correct dataset as we add methods for enabling them.

Using Java 8 functional style conventions an example event sending code looks like this:

    public class EventClientExample {
    
        static Logger log = LoggerFactory.getLogger(EventClientExample.class);
    
        public static void main(String[] args) {
    
            String datasetId = "test-resource";
            
            // create a client to send events to a dataset
            EventClient client = new EventClient(datasetId, "localhost", 8080);
    
            // some example json events from a running site
            String fileName = "data/sample-x.json";
    
            try (BufferedReader br = Files.newBufferedReader(Paths.get(fileName))) {
    
                List<String> events = br.lines()//.limit(100)
                        .collect(Collectors.toList());
    
                log.info("Send events: " + events.size());
    
                long start = System.currentTimeMillis();
                
                // This is the key part of the code 
                // "client.createEvents(event) it sends the json string 
                // through the client created above 
                client.createEvents(events).thenApply(pairs -> {
                    long duration = System.currentTimeMillis() - start;
                    Map<String, Long> counting = pairs.stream()
                            .map(p -> p.second().first().toString())
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    
                    log.info("Finished: " + duration + "ms. Responses: " + pairs.size());
                    counting.forEach((code, cnt) -> log.info("Status " + code + ": " + cnt));
                    return pairs.size();
                }).whenComplete((size, throwable) -> {
                    log.info("Complete: " + size);
                    log.info("Close client");
                    client.close();
                });
    
            } catch (IOException e) {
                log.error("Oh no!", e);
                client.close();
            }
    
        }
    
    }

The important bit here is creating a client:

    EventClient client = new EventClient(datasetId, "localhost", 8080);
    
The datasetId must be a string the uniquely ids the server-side dataset. The id is ignored currently so all data goes into the single dataset. The host address must be the remote pio-kappa server. The port can be changed but is 8080 by default.

Then send a json string

## Apache PredictionIO-0.10.0 Java Client Input and Query SDK (For comparison only)

The PIO 0.10.0 client is [here](https://github.com/apache/incubator-predictionio-sdk-java).

The old style Java SDK has 2 clients, [one for input](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EventClient.java) and [one for queries](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java), The PIO-Kappa SDK will have one client for all APIs deriving resource endpoints from resource-ids and the PIO-Kappa server address.

### Input

The `Event` class should be instantiated in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/Event.java) A new route should be created for input derived from the new REST server address:port and the new `datasets` resource-id.

### Query

A query Map should be converted into a JSON payload in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java#L93) as the old SDK. A new route will be derived from the PIO-Kappa Server address:port and the `engines` resource-id.

