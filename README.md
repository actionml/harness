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

# Microservices

There are 2 types of microservices in Harness:

 1. **REST-Based**: These present an HTTP API, optionally with TLS (SSL) and Auth (Authentication and Authorization). This type of microservice is used for the Main Harness API as well as the Auth-Server. Harness by default uses no TLS or Auth but can have both turned on so that it is secure to run across the Internet with a the provided Java and Python SDKs which also support TLS and Auth. The Auth-Server is also an REST microservice but due to the need to access it for every Harness request (need for speed) does not use Auth or TLS. Furthermore is uses caching to make most access unnecessary.
 2. **Actor-Based**: Here the need is to have lightweight fast microservices that can scale easily (with no design constraints put on the engine API) and in a fault resilient way. Here we use an akka System of Actors spanning multiple nodes attached to an akka Event Bus. All engines are Actors and are stateless (relying on persistence stores where persistence is needed) so any Universal Recommender Actor with the same config can respond to any Event meant for it. This allows scaling across nodes in a failure resilient manner since the akka Event Bus provides mechanisms for failure detection and recovery

Harness and the Auth-Server are REST microservices, Engines are Actor-based microservices.

# Architecture

![Harness Logical Archite4cture](https://docs.google.com/drawings/d/1SjMDyc16BzHmItpAZuOGIGzbMdlWceK8TM9kde1Ty94/pub?w=908&h=753)

![Scalable EngneCluster](https://docs.google.com/drawings/d/e/2PACX-1vTjT_hV6pz3Fv5blx9p18NucRXFWzJrHqdG-lDMh7GBYur6JOUcFBgDsh5dJVnhH7JkB2wtB7QewmRK/pub?w=1576&h=1364)

## Harness Core

Harness is a REST server with an API for Engines, Events, Queries, Users, and Permissions. It, in effect proxies the Users and Permissions APIs so as to delegate the logic for dealing with these to the Auth-Server since it is that server's concern to authorize and authenticate all Harness requests.

The Harness Server is also in charge of the CRUD operations on Engines and other administrative operations. It presents the only externally available 
API, which is identical for any Client software, be it an application or the Command Line Interface (CLI), or some future WEB UI.

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints that can be attached at runtime to rsource IDs and Engine classes. It is meant as a core piece for HTTP microservices to use in presenting a REST interface and also supports SSL, signature based authentication, and REST route based authorization.

The Router has an API to create endpoints and attach Akka Actors to them for handling incoming requests. This is used to specialize the Router for the work of the particular microservices used. 

## Administrator

The Administrator executes CRUD type operations on Engine. It will also deal with CLI extensions that are of no concern to the Engines, like status reports, and scheduling of Commands (not implemented yet)

## Templates and Engines

A Template is an Abstract API that needs to be, at least partially implemented by the Engine. They are seen in `com.actionml.core.templates` module. Each Engine must supply required APIs but what they do when invoked is entirely up to the Engine. Templates define an engine type, Engines are instantiated from Templates using parameters found in the engine's JSON file, including a companion object with a factory method. This file structure is very flexible and can contain any information the Engine needs to run, including compute platform information that is not generic to Harness.

## The Cluster of Nodes

In V1 Harness is monolithic, requiring one Engine for every EngineID. This scales only vertically. To solve this Harness V2 will implement an akka Cluster of Nodes, where Engines can be spread out over the cluster. Each Engine will be connected to a akka Event Bus and able to respond to any Event targeted for its Engine type (defined by factory classname, config JSON signature which itself includes the Engine ID). So many Engines may respond to any Event for a specific Engine, allowing Engines to scale horizontally.

## Event Bus

The Event Bus presents Events with IDs that are classified to belong to some form of Actor, in Harness this is usually and Engine with a specific 
signature that is derived from the JSON config file, which includes the fartory object, the engines resource id, and all params used in the algorithm. The first available Engine that can respond grabs the Event and processes it by validating it (which may cause an HTTP error status code) and processing it as the Engine sees fit eventually responding with an HTTP code. 

The Event Buss can have multiple Engines that have the same Engine ID and so may share the work of processing the Event. This provides vertical scalability, and the akka Event Bus also provides and error recovery mechanism to handle failures, providing failure resiliency.

The Event Bus is not in Harness V1 but in V2 it is our design goal to provide some form of auto scalability or feedback mechanism where responses for a type of Engine become too slow. 

## Compute Engines

The most obvious and common Compute Engine is Apache Spark but it is the4 responsibility of the Engine to specify and use the Compute Engine needed. For instance VW is used be the Contextual Bandit, and others may use Tensor flow, or other appropriate Engines. Some common ones will have APIs associated with them or the Engine may choose to deal with the one provided by the engine.

## Mirror Event Store

Events may be any valid form of JSON. We often choose to follow the conventions created by the Apache PredictionIO Project so we can maintain data level compatibility with PIO templates. However due to the fact that Kappa-style online learners do not store events but discard them nce their model is updated we provide a method to mirror the event for replay while debugging an Engine or while learning to send the right events to the Engine. Engines validate events and will respond with HTTP errors when malformed events come in. This can only be know by the Engine so the raw JSON events are mirrored even if they are not valid so they are more easily refined and fixed. Once the events pass the engine's validation this mechanism can also be used as a form of backup for Kappa online learners since playback of all valid events will restore the correct state of the model at the end. 

For Lambda-style learners the mirror plays the same role as a sort of automatic backup, with built-in controls for how many events are preserved in rotation form. in any Lambda system events cannot be allowed to accumulate forever so most Lambda engines implement some form of moving time window (imaging 1 year of events for a big ecommerce site) this can also be used to limit the number of events mirrored.

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

We will use bearer token OAuth2 to authenticate connections and identify the server making connections. This form is very much like basic Auth with session temp auth ids to speed the protocol. This is built into the Java and Python SDK and so is simple to implement on the client side.

# Authorization

ACLs are set through the Harness CLI so new tokens/signatures can be granted permission for CRUD access to REST resources and can be added at runtime. Each token may be granted access to all or a small subset of REST resources. This is useful for administration where the CLI needs to have access to all resources ] and for external users who may only have access to a certain engine-id with the associated `events` and `queries`. See the [Commands](commands.md) for details.

# [Config](harness_config.md)

Out of the box Harness runs on localhost and does not mirror events. To change this and other global behavior (not engine specific) read the [Config Page](harness_config.md)
