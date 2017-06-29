[![Build Status](https://travis-ci.org/actionml/pio-kappa.svg?branch=master)](https://travis-ci.org/actionml/pio-kappa)

# Harness (was Pio-kappa) Overview

This project implements a microservice based Machine learning server similar to the existing PredictionIO (v0.11.0 currently) but with
several fundamental changes including:

 - microservice based
 - merged input and query servers on different endpoints of the same REST service
 - based on http-akka
 - supports SSL
 - supports authentication (server to server signature based OAuth2)
 - supports Kappa style and non-Spark Algorithms in Templates with no requirement that Spark be installed or used
 - Templates must supply code for validation of input
 - Java and Python SDKs implements client side SSL and Authentication
 - implements the Command Line Interface (CLI) as calls to the REST mircorservice using SSL and Authentication in Python with thin layer of Bash script
 - supports both mutable objects and an immutable event stream in an integrated manner allowing TTLs of certain types of events and instant modification of mutable objects
 - multi-tenancy of multiple engines with queries and input datasets supported in the same way at a fundamental level based on REST resource IDs
 
# Requirements

The new Harness server should take identical input (events) and respond to identical queries packaged in JSON as Apache PIO using 
the Contextual Bandit as an example. We use the CB because it can operate in Lambda and Kappa style and already has a well 
defined input/query objects

 
# Architecture
 
**Components**

 - akka-http, akka-actors, scala
 - [Asynch HTTP Client](https://github.com/AsyncHttpClient/async-http-client#async-http-client-asynchttpclient-on-twitter-)

![Harness Overview](https://docs.google.com/drawings/d/1SjMDyc16BzHmItpAZuOGIGzbMdlWceK8TM9kde1Ty94/pub?w=908&h=753)

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints that can be attached at runtime. It is meant as a core piece for microservices to use in presenting a REST interface and also support SSL and signature based OAuth2. These features are optional for times Harness needs to be available on the open internet from client application, rather than behind VPNs.

The Router has an API to create endpoints and attach Akka Actors to them for handling incoming requests. This is used to specialize the Router for the work of the microservice. 

# Kappa Learning

The Kappa style learning algorithm takes in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in Harness Templates in [Kappa Learning](kappa-learning.md)
 
# Server REST API

All REST APIs will have Access Control Lists based on who is allowed to access the endpoint and resourse-id. All APIs will respond with an appropriate HTTP code, some (UPDATE/POST requests) will respond with a JSON  body as described. All data not defined in the URI will be in JSON request and response body.

Integral to REST is the notion of a "resource", which can be though of as a collection of items that can be addressed by a resource-id. Since with REST all resource-ids must be URI encoded following the rules for vanilla 
URI fragments. The resources defined in Harness are:

 - **engine**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries.
 - **events**: sub-collections that make up a particular dataset. They are addressed POST `/engines/<engine-id>/events/` for adding. Events are loosely defined in JSON with engine specific fields. Unreserved events (no $ in the name) can be thought of as a non-ending stream. Reserved event like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Template description for how events are formatted, validated, and processed. 
 - **queries**: queries are made to engines to return information based on their models. since they are completely Template specific their format, validation, and results are described in the Template documentation.
 - **commands**: pre-defined commands that perform workflow or administrative tasks. These may be synchronous, returning results with the HTTP response or asynchronous, where they must be polled for status since the command may take very long to complete.

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending PIO events and making queries.

    POST /engines/<engine-id>/events
        Request Body: JSON for PIO-like event
        Response Body: na
        
    POST /engines/<engine-id>/queries
        Request Body: JSON for query
        Response Body: JSON for results

## The Commands

Commands are REST resources just like Engines so Commands can be fired through REST but we also provide a Command Line Interface (CLI) to allow quick access and control of the server and to support scripted interactions. See [Commands](commands.md)
     
# [Security](security.md)  

pio-kappa optionally supports SSL and Server to Server Authentication. See the [Security](security.md) section for more details.
    
# [Java SDK](java-sdk.md)

The Java SDK is currently source and build instructions. You must include the source and required Java artifacts a shown in the examples then build them into your Java application.

# [Python CLI and SDK](commands.md)

The CLI will be implemented using the new Python SDK supporting the SSL and authentication methods where needed. 
  
# Access Control Lists

ACLs are set through the Harness CLI so new signatures can be generated and permission granted to endpoints that being added at runtime. Each signature may be granted access to all or a small subset of endpoints. This is useful for administration where the CLI needs to have access to all endpoints and for external clients who may only have access to a certain engine-id with the associated `events` and `queries` endpoints. See the [Commands](commands.md) for details.
