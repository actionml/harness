[![Build Status](https://travis-ci.org/actionml/pio-kappa.svg?branch=master)](https://travis-ci.org/actionml/pio-kappa)

# Pio-kappa Overview

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

 - akka-http, akka-actors, scala
 - [Asynch HTTP Client](https://github.com/AsyncHttpClient/async-http-client#async-http-client-asynchttpclient-on-twitter-), which replaces previous PIO SDKs and supports SSL with S2S Auth
 - Example existing [PIO Java client SDK](https://github.com/apache/incubator-predictionio-sdk-java)

![PIO Kappa Architecture](https://docs.google.com/drawings/d/1SjMDyc16BzHmItpAZuOGIGzbMdlWceK8TM9kde1Ty94/pub?w=910&h=739)

## Router

The pio-kappa core is made from a component called a router, which maintains REST endpoints that can be attached in runtime. It is meant as a core piece for microservices to use in presenting a REST interface and also support SSL and S2S Auth. It may be more desirable in a larger system to use other forms of Auth or implement SSL in load balancing or proxies but it is supplied for extra security and where these systems are not needed.

The Router has an API to create endpoints and attach Akka Actors to them for handling incoming requests. This is used to specialize the Router for the work of the microservice. 

**Note**: for a first step the Router may be linked to microservices, rather than being updated while running through it's private REST API.

# Kappa Learning

The Kappa style learning algorithm takes in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in PIO-Kappa Templates in [Kappa Learning](kappa-learning.md)
 
# Server REST API

All REST APIs will have Access Control Lists based on who is allowed to access the endpoint and resourse-id. All APIs will respond with an appropriate HTTP code, some (UPDATE/POST requests) will respond with a JSON  body as described. All data not defined in the URI will be in JSON request and response body.

Integral to REST is the notion of a "resource", which can be though of as a collection of items that can be addressed by a resource-id. Since with REST all resource-ids must be URI encoded following the rules for vanilla 
URI fragments. The resources defined in PIO-Kappa are:

 - **datasets**: a collection of datasets that store events
 - **events**: sub-collections that make up a particular dataset. They are addressed liek `/datasets/<dataset-id>/events/` for adding. Events are loosely defined in JSON with engine specific fields. Unreserved events (no $ in the name) can be thought of as a non-ending stream. Reserved event like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the engine description for how events are formatted and handled.
 - **engine**: the engine is the instance of a template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries.
 - **commands**: pre-defined commands that perform workflow or administrative tasks. These may be synchronous, returning results with the HTTP response or asynchronous, where they must be polled for status since the command may take very long to complete.

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending PIO events and making queries.

    POST /datasets/<dataset-id>/events
        Request Body: JSON for PIO event
        Response Body: na
        
    POST /engines/<engine-id>/queries
        Request Body: JSON for PIO query
        Response Body: JSON for PIO PredictedResults

## The Commands

Commands are REST resources just like Datasets and Engines so commands can be fired through REST but we also provide a Command Line Interface (CLI) similar to Apache PredictionIO. It allows you quickly access and control the server and to script interactions. See [Commands](commands.md)
     
# [Security](security.md)  

pio-kappa optionally supports SSL and Server to Server Authentication. See the [Security](security.md) section for more details.
    
# [Java SDK](java-sdk.md)

The Java SDK is currently source and build instructions. You must include the source and required Java artifacts a shown in the examples then build them into your Java application.

# 

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
