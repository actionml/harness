# pio-kappa
PredictionIO-Kappa migration

This project implements a microservice based Machine learning server similar to the exisitng PredictionIO (v0.10.0 currently) but with
several fundimental changes including:

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

All REST APIs will have Access Control Lists based on who is allowed to access the endpoint and resourse-id. 

## Input and Query

    POST /datasets/<dataset-id>/events {JSON body for PIO event}
        Response: HTTP code
    POST /engines/<engine-id>/queries {JSON body for PIO query}
        Response: HTTP code
        Body: JSON for PIO PredictedResults

## Commands

    POST /datasets/<dataset-id> {JSON body for PIO empty dataset creation}
        Response: HTTP code
    DELETE /datasets/<dataset-id> {JSON body for PIO empty dataset deletion}
        Response: HTTP code
    POST /engines/<engine-id> {JSON body for engine configuration engine.json file}
        Response: HTTP code
    POST /commands/as-batch-train/engines/<engine-id>
        Response: HTTP code
        Body: returns the command-id to poll for information about command progress
        Action: will launch batch training of an <engine-id>
    GET /commands/<command-id> {JSON response body command status for asynch/long-lived commands}
        Response: HTTP code
    
# Java SDK API

 - Supports all Server REST for Input and Query
 - packages JSON for REST call
 - implements SSL and auth
 - modeled after the PredictionIO Java SDK API where possible

# Python CLI

 - Supports all commands executable from the command line
  - `pio engine create --engine <path-to-engine.json> --engine-id <engine-id>`
    Creates an engine instance with the supplied id, attaches it to the server at `/engines/<engine-id>`
    The engine definition will include Tempalte code referenced in the engine.json. The `predict` method is attached to the server
    at `POST /engines/<engine-id>/queries {JSON query body}` using an AKKA Actor.
  - `pio dataset create --dataset <dataset-id>`
  
# Access Control Lists

ACLs are set through config in `pio-kappa/conf/access.json` Format TBD but will include all fragments of the REST URI.
