[![Build Status](https://travis-ci.org/actionml/pio-kappa.svg?branch=master)](https://travis-ci.org/actionml/pio-kappa)

# Harness Overview

This project implements a microservice based Machine learning server similar to the existing PredictionIO (v0.11.0 currently) but with
several fundamental changes including:

 - microservice based
 - merged input and query servers on different endpoints of the same REST service
 - based on http-akka
 - supports SSL
 - supports authentication (server to server bearer token based)
 - supports Kappa style online learners
 - support non-Spark Algorithms in Templates with no requirement that Spark be installed or used
 - supports Lambda style batch learners
 - Templates must supply code for validation of input so input is validated in realtime
 - Java and Python SDKs implements client side SSL and Authentication
 - implements the Command Line Interface (CLI) as calls to the REST microservice using SSL and Authentication in Python
 - supports both mutable objects and an immutable event stream in an integrated manner allowing TTLs of certain types of events and instant modification of mutable objects
 - multi-tenancy of multiple engines with queries and input datasets supported in the same way at a fundamental level based on REST resource IDs
 
# Requirements

The new Harness server should take identical input (events) and respond to identical queries packaged in JSON as Apache PIO using 
the Contextual Bandit as an example. We use the CB because it can operate in Lambda and Kappa style and already has a well defined input/query objects for both PIO and Harness.
 
 - akka-http for router and server implementation
 - akka-actors for lightweight engine containers
 - akka-http Java Client for Harness Java SDK
 - scala 2.11
 - Python 3 for CLI and Harness Python SDK
 - MongoDB 3.x (switchable with effort)

# Architecture

![Harness Overview](https://docs.google.com/drawings/d/1SjMDyc16BzHmItpAZuOGIGzbMdlWceK8TM9kde1Ty94/pub?w=908&h=753)

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints that can be attached at runtime. It is meant as a core piece for microservices to use in presenting a REST interface and also support SSL, signature based authentication, and REST route based authorization. These features are optional so Harness can be available on the open unsecured environments like the internet or disabled for operation behind VPNs.

The Router has an API to create endpoints and attach Akka Actors to them for handling incoming requests. This is used to specialize the Router for the work of the microservice. 

## Administrator

The Administrator executes all CLI type commands. It restores all Engines to their previous state on `harness start` and is in charge of maintaining metadata about the Engines. Another example of its function is `harness add -c <engine-json-file>` where it creates an engine from the factory in the json file, initializes is, stores metadata about it, and creates the routes to the Engine endpoints as specified by the Templates contract. All Template specific behavior is deferred to the Engine.

## Templates and Engines

Templates are abstract APIs defined in the `com.actionml.core.templates` module. Each Engine must supply required APIs but what they do when invoked is entirely up to the Engine. Templates define an engine type, Engines are instantiated from Templates using parameters found in the engine's json file. This file structure is very flexible and can contain any information the Engine needs to run, including compute platform information that is not generic to Harness.

# Kappa Learning

The Kappa style learning algorithm takes in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in Harness Templates in [Kappa Learning](kappa-learning.md)

# Lambda Learning

Many algorithms learn by processing a large batch of data and modifying some model all at once but periodically. The Universal Recommender is an example of a Lambda learner. Spark's MLlib also has examples of Lambda style learners. 
 
# Server REST API

All REST APIs are protected by authorization lists, which specify which routes are accessible to which clients. All APIs will respond with an appropriate HTTP code, some (UPDATE/POST requests) will respond with a JSON  body as described. All data not defined in the URI will be in JSON request and response body.

Integral to REST is the notion of a "resource", which can be though of as a collection of items that can be addressed by a resource-id. Since all REST resource-ids must be URI encoded following the rules for vanilla 
URI fragments when naming resources. The resources defined in Harness are:

 - **engine**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries.
 - **events**: sub-collections that make up a particular dataset used a specific Engine. To send data to an Engine simply `POST /engines/<engine-id>/events/` a JSON Event whose format is defined by the Template. Non-reserved events (no $ in the name) can be thought of as a unending stream. Reserved eventa like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Template description for how events are formatted, validated, and processed. 
 - **queries**: queries are made to engines to return information based on their models. since they are completely Template specific their format, validation, and results are described in the Template documentation.
 - **commands**: pre-defined commands that perform workflow or administrative tasks. These may be synchronous, returning results with the HTTP response or asynchronous, where they must be polled for status. An example of this is when a command may take long to complete.

For the full Harness REST API and response codes, see the [Harness REST Specification](rest_spec.md)

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending Engine events and making queries. The SDK handles TLS and Authentication, and Authorization based on OAuth2 bearer tokens so it is recommended to use an SDK but since we follow standard OAuth2 one can create their own requests and SDK.

Disregarding the optional TLS and Auth, simple input and queries look like this:

    POST /engines/<engine-id>/events
        Request Body: JSON for PIO-like event
        Response Body: na
        
    POST /engines/<engine-id>/queries
        Request Body: JSON for query
        Response Body: JSON for results

For specifics of the format and use of events and queries see the Template documentation. For example the [Contextual Bandit docs](the_contextual_bandit.md). For the full API requests and response codes see the [Harness REST Specification](rest_spec.md)

# [Commands](commands.md)

Commands that do not correspond to an Engine function are REST resources just like Engines so Commands can be fired through REST but we also provide a Command Line Interface (CLI) to allow quick access and control of the server and to support scripted interactions. See [Commands](commands.md)
     
# [Security](security.md)  

pio-kappa optionally supports SSL and Server to Server Authentication. See the [Security](security.md) section for more details.
    
# [Java SDK](java-sdk.md)

The Java SDK is currently source and build instructions. You must include the source and required Java artifacts as shown in the examples then build them into your Java application.

# [Python CLI and SDK](commands.md)

The CLI is implemented using the new Python SDK supporting the SSL and authentication methods where needed. In `
  
# Authentication

We will use bearer token OAuth2 to authenticate connections and identify the server making connections. This is built into the Java and Python SDK and so is simple to implement on the client side.

# Authorization

ACLs are set through the Harness CLI so new tokens/signatures can be granted permission for CRUD access to REST resources and can be added at runtime. Each token may be granted access to all or a small subset of REST resources. This is useful for administration where the CLI needs to have access to all resources ] and for external users who may only have access to a certain engine-id with the associated `events` and `queries`. See the [Commands](commands.md) for details.

# [Config](harness_config.md)

Out of the box Harness runs on localhost and does not mirror events. To change this and other global behavior (not engine specific) read the [Config Page](harness_config.md)
