# Simple REST API

The REST API contains many things that are meant for admin usage and optional authentication and authorization. By far the typical use will be greatly simpler.

Most clients will use only 2 of the REST APIs:

 - **Input**: sending input events from an Application Server to a Harness Engine Instance.
 - **Queries**: sending queries and receiving results to/from a Harness Engine Instance.

# Creating an Engine Instance

In order to send input, import, get status, or query an 
Engine Instance you must first create one. An Engine Instance is created from some Engine type (like the UR) with a JSON configure file. 

To create the instance use the Admin command `harness add /path/to/engine/config/json`. The JSON contains parameters for the Engine Type like algorithm parameters but also some required things that allow Harness to route the input Events to the correct Engine Instance. The required part of the JSON looks like this:

```{
  "engineId": "some_engine_id",
  "engineFactory": "com.actionml.engines.someEngineFactory",
  "algorithm": {
    ...
  }
}
```

 - **`engineId`**: This is the resource ID, in the REST sense. It is used in a URI (Universal Resource Identifier) to address the specific Engine Instance for any REST API&mdash;including input Events and Queries.

    Once the Engine Instance is added it will be referred to in the CLI and the REST API using the `engineId` value, so in the example above you could get the status of the Engine Instance with the Admin Command `harness status engines some_engine_id`
    
    It is preferred to us camel_case since the `engineId` value will be used in a URI.
 - **engineFactory**: this is the full class name of the "factory" that can instantiate the Engine Instance with the rest of the parameters.
 - **others**: the rest of the params are optional and control the Engine Instances behavior and algorithm tuning, etc. See the specific Engine config documentation for their meaning.

# Input

From the Engine Instance config we know a unique ID for the instance. We can now combine it with other information to create a URI to use in sending input. The actual input payload will be JSON in a format defined by the Engine type (UR for example)

For the UR input (as an Example) you can use `curl` to send input like this:

```
$ curl -i -X POST http://<harness-address:9090>/engines/<some-engine-id>/events \
-H "Content-Type: application/json" \
-d '{
   "event" : "purchase",
   "entityType" : "user",
   "entityId" : "John Doe",
   "targetEntityType" : "item",
   "targetEntityId" : "iPad",
   "eventTime" : "2015-10-05T21:02:49.228Z"
}
```

**Notice the use of the `events` endpoint**

If we know the Harness Server address and the Engine ID from the Engine Instances JSON config we can form the URI:

```http://<harness-address:9090>/engines/<some-engine-id>/events```

An HTTP/HTTPS 201 "resource created" response means the Event was created. If it is invalid you will get some error like 400 "bad request" meaning the event may have been malformed or was invalid.

# Queries

Likewise for sending queries, the Engine Instance must have beed created with `harness add ...` For Lambda Engines like the UR you will also need to create a model in order to return results, this is done by performing the 
Admin Command `harness train some_engine_id`. If the training succeeds (see the harness logs) then the model exists and is ready for Queries.

Forming a Query is much like forming an Event. We need the address of the Harness server and the `engineId` value from the JSON config.

The Query payload is in the JSON request body and is of a format specific to the Engine type. This is an example for the UR:

```
$ curl -i -X POST http://<harness-address:9090>/engines/<some-engine-id>/queries \
-H "Content-Type: application/json" \
-d '{
  "user": "John Doe"
}
```

**Notice the use of the `queries` endpoint**

See your Engine's documentation for Query formats and use.

# Summary

In the examples below we send input and queries to the a UR Engine Instance. Replace angle bracket `<text>` with your information.

 - **Input**: 

    ```
    POST: 
      http://<harness-address:9090>/engines/<some-engine-id>/events
    JSON BODY: 
      {
        "event" : "purchase",
        "entityType" : "user",
        "entityId" : "John Doe",
        "targetEntityType" : "item",
        "targetEntityId" : "iPad",
        "eventTime" : "2015-10-05T21:02:49.228Z"
      }
    ```

 - **Query**:

    ```
    POST: 
      http://<harness-address:9090>/engines/<some-engine-id>/queries
    JSON BODY: 
      {
        "user": "John Doe"
      }
    ```

# More on Harness REST

See the full the [Harness REST Spec](rest_spec.md) for more information.  