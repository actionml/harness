[![Build Status](https://travis-ci.org/actionml/pio-kappa.svg?branch=master)](https://travis-ci.org/actionml/pio-kappa)

# pio-kappa
PredictionIO-Kappa migration

This project implements a microservice based Machine learning server similar to the existing PredictionIO (v0.10.0 currently) but with
several fundamental changes including:

 - microservice based
 - merged input and query servers on different endpoints of the same REST service
 - based on http-akka
 - supports SSL
 - supports authentication (server to server in some form)
 - supports Kappa style and non-Spark Algorithms in Templates
 - has an integrated example SDK in Java that implements client side SSL and Authentication
 - implements the Command Line Interface (CLI) as calls to the REST mircorservice
 
# Requirements

The new PIO-Kappa server should take identical input (events) and respond to identical queries packaged in JSON as Apache PIO using 
the Contextual Bandit as an example. We use the CB because it can operate in Lambda and Kappa style and already has a well 
defined input/query objects

 
# Architecture
 
**Libraries used**

 - akka-http, scala
 - [Asynch HTTP Client](https://github.com/AsyncHttpClient/async-http-client#async-http-client-asynchttpclient-on-twitter-) (if possible)
 - existing [PIO Java client SDK](https://github.com/apache/incubator-predictionio-sdk-java)
 
# Server REST API

All REST APIs will have Access Control Lists based on who is allowed to access the endpoint and resourse-id. All APIs will respond with an appropriate HTTP code, some (UPDATE/POST requests) will respond with a JSON  body as described. All data not defined in the URI will be in JSON request and response body.

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending PIO events and making queries.

    POST /datasets/<dataset-id>/events
        Request Body: JSON for PIO event
        Response Body: na
    POST /engines/<engine-id>/queries
        Request Body: JSON for PIO query
        Response Body: JSON for PIO PredictedResults

## Commands

    POST /datasets/<dataset-id>
        Request Body: JSON for PIO dataset description
        Response Body: JSON describing Dataset created
        Action: sets up an empty dataset with the id specified if `_new` is passed in a dataset-id will be generated and returned to id the dataset.
    DELETE /datasets/<dataset-id>
        Action: deletes the dataset including the dataset-id/empty dataset and removes all data
    POST /engines/<engine-id> 
        Request Body: JSON for engine configuration engine.json file
        Response Body: description of engine-instance created
    POST /commands/batch-train/engines/<engine-id>
        Request Body: na
        Response Body: returns the command-id to poll via GET for information about command progress
        Action: will launch batch training of an <engine-id>
    GET /commands/<command-id> 
        Response Body: response body command status for asynch/long-lived command
        
## SSL Support

Uses [http-akka SSL support](http://doc.akka.io/docs/akka-http/current/scala/http/server-side-https-support.html).

## OAuth2 Authentication

We will use OAuth2 authorization framework. OAuth2 defines 4 grant types: "authorization code", "implicit", "password credentials", and "client credentials". Since we need server-to-server auth, then we will use ["client credentials" grant type](https://tools.ietf.org/html/rfc6749#section-4.4) Thus our rest service will be a "resource server" and "authorization server". We will use the [Nulab library](https://github.com/nulab/scala-oauth2-provider) for the implementation.  
    
# Java SDK API

 - Supports all Server REST for Input and Query
 - packages JSON for REST call
 - implements SSL and auth
 - modeled after the PredictionIO Java SDK API where possible
 - written based on [http-akka client](http://doc.akka.io/docs/akka-http/current/java/http/introduction.html#http-client-api)

## Java Client Input and Query SDK

PIO-0.10.0 vs PIO-Kappa Java Input and Query SDK API. The PIO 0.10.0 client is [here](https://github.com/apache/incubator-predictionio-sdk-java).

The old style Java SDK has 2 clients, [one for input](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EventClient.java) and [one for queries](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java), The PIO-Kappa SDK will have one client for all APIs deriving resource endpoints from resource-ids and the PIO-Kappa server address.

### Input

The `Event` class should be instantiated in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/Event.java) A new route should be created for input derived from the new REST server address:port and the new `datasets` resource-id.

### Query

A query Map should be converted into a JSON payload in the [same manner](https://github.com/apache/incubator-predictionio-sdk-java/blob/develop/client/src/main/java/io/prediction/EngineClient.java#L93) as the old SDK. A new route will be derived from the PIO-Kappa Server address:port and the `engines` resource-id.


# Python CLI and SDK

The CLI will be implemented using the new Python SDK, which like the Java SDK will be a superset of the PIO Python SDK where possible and support the new SSL and authentication methods.

 - Supports all commands executable from the command line
  - `pio engine create --engine <path-to-engine.json> --engine-id <engine-id>`
    Creates an engine instance with the supplied id, attaches it to the server at `/engines/<engine-id>`
    The engine definition will include Tempalte code referenced in the engine.json. The `predict` method is attached to the server
    at `POST /engines/<engine-id>/queries {JSON query body}` using an AKKA Actor.
  - `pio dataset create --dataset <dataset-id>`
  
# Access Control Lists

ACLs are set through config in `pio-kappa/conf/access.json` Format TBD but will include all fragments of the REST URI.
