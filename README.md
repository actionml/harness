[![Build Status](https://travis-ci.org/actionml/pio-kappa.svg?branch=master)](https://travis-ci.org/actionml/pio-kappa)

# Harness Overview

This project implements a microservice based Machine Learning Server. It provides an API for plug-in Engines (the API is called a Template) that implements some Algorithm and provides the scaffolding to implement all input and query serving needs including the following features:

 - **TLS/SSL** support from akka-http on the Server as well as in the Java and Python Client SDKs
 - **Authentication** support using server to server bearer tokens, similar to basic auth but from the OAuth 2.0 spec. Again on both Server and Client SDKs.
 - **Microservice Architecture**:
     - **REST-based**: for slightly more Heavy-weight HTTP(S) based separate process microservices with REST APIs using optional TLS and Auth. The Auth server is implemented, for example, using the REST Microservice framework via akka-http.
     - **Actor-based** light-weight microservices based on the akka Event Bus for clustered scalable lightweight microservices
 - **Single REST API** for input, query, and commands
 - **Flexible Learning Styles**:
    - **Kappa**: Online learners that update models in realtime
    - **Lambda**: Batch learners that update or re-compute models in the background during realtime input and queries.
    - **Hybrids and Reinforcement Learning**
 - **Compute-Engine Neutral**: supports any compute engine or pre-packaged algorithm library that is JVM compatible. For example Spark, TensorFlow, Vowpal Wabbit, MLlib, Mahout, ... Does not require Spark, or HDFS.
 - **Scalability**: Engines implemented using either microservice method  and either leanring style can be scaled by deploying on multiple nodes. The simplest way to do this is using the Light-weight Actor-based microservice method or by basing the algorithm's implementation on clustered services like Spark.
 - **Scaldi Implementation Injection**:
     - **Main Metadata Store**: is injected at server startup and only supports MongoDB at present.
     - **Input Mirroring**: can be either the server machine's file system or HDFS and the implementation is injected at startup and controlled by configuration
     - **DAL Abstraction**: Virtually any store can be implemented using the DAL/DAO/DAOImpl Pattern (Data Access Layer). There is an example included using MongoDB. 
     - **General Plugability**: implemented through Scaldi dependency injection, often based on configuration parameters. This is available to all Engines
 - **Realtime Input Validation**: Though optional, Engines are encouraged to supply realtime input validation and a framework based on the Cats library is implemented to support this.
 - **Client Side Support**: As stated above SDKs implement support for TLS/SSL and Auth and are designed for asynchronous non-blocking use for the highest throughput possible (though synchronous blocking use is also supported)
     - **Java SDK**: is supplied for Event and Query endpoints 
     - **Python SDK**: implements client side for all endpoints and is used by the Python CLI.
     - **REST without SSL and Auth** is simple and can use any HTTP client&mdash;curl, etc. SSL and Auth complicate the client but the API is well specified.
 - **Command Line Interface (CLI)** is implemented as calls to the REST API and so is securely remotable.
 - **Secure Multi-tenancy**: will run multiple Engines with separate Permissions
     - **Multiple Engine-IDs**: allow any number of variations on one Engine type or multiple Engine types. One Engine is the simplest case. By default these all run in a single process and so are lightweight.
     - **Multiple Permissions**: allow user+secret level access control to protect one "tenant" from another. Or Auth can be disabled to allow all Engines access without a user+secret for simple deployments.
 - **Mutable Object and Immutable Event Streams**: can coexist in Harness allowing the store to meet the needs of the algorithm.
 - **User and Permission Management**: Built-in user+secret generation with permissions management at the Engine instance level.
 - **Data Set Compatibility** with Apache PredictionIO is possible as is the case with the Contextual Bandit Engine, which exists in a PredictionIO Template. This is not enforced and the various data objects sent to or received from an Engine through Harness are completely flexible and can be anything encodable in JSON.
 - **Async SDKs and Internal APIs**: both our outward facing REST APIs as seen from the SDK and most of our internal APIs including those that communicate with Databases are based on Asynchronous/non-blocking usage.
 - **Provisioning** can be done by time proven binary installation or optionally (where ease of deployment, configuration, or scaling is required) using modern container methods:
    - **Containerized** optional containerized provisioning using Docker
    - **Container Orchestration** optional container orchestration with Kubernettes
    - **Instance Creation** optional compute and storage resource creation using cloud platform neutral Terraform

**Note**: not all of the above are implemented in early versions, see [version history](versions.md)  for specifics
 
# Requirements

In its simplest form Harness has few external requirements. To run it on one machine the requirements are:
 
 - Python 3 for CLI and Harness Python SDK
 - Pip 3 to add the SDK to the Python CLI
 - MongoDB 3.x (pluggable via a clean DAO/DAL interface with injected DAOImpl for any DB)
 - Scala 2.11
 - Some recent `nix OS

Each Engine has its own requirements driven by decisions like what compute engine to use (Spark, TensorFlow, Vowpal Wabbit, DL4J, etc) as well as what Libraries it may need. In General these requirements will be JVM accessible but even this is flexible when using REST-based microservices to implement Engines. See specific Engines for their extra requirements. 

# Microservices

There are 3 types of Engine microservices in Harness:

 1. **In-Process Multithreaded Engine**: these are implemented in a lightweight performant way that can scale by using scalable Services like Spark for computing and Hadoop's HDFS for storage
 2. **Remote akka Actor-based Engine** We use an akka System of Actors spanning multiple nodes attached to an akka Event Bus. This type of engine can be scaled by adding multiple identical Engines to the Event bus all processing a single input or request then waiting for more. This also allows for the use of scalable services like Elasticsearch for K-Nearest Neighbor serving of Spark for computing.
 3. **Remote REST-based Engine**: Engines may be deployed as separate REST-microservices in containers or on separate machines. This allows microservice Engines to be developed in any language that can have a REST-based "Client" to the Harness Engine REST-based Proxy. We plan to support Python with a Proxy and Client so Engines can be in Python, but any language that can support a REST client and JSON data exchange can be used to implement Engines in this fashion.

![Harness Engine Flavors](https://docs.google.com/drawings/d/e/2PACX-1vSbM2ZDzUJ9JPcOODKpmvuB5F1giDNM4lGFhP5E_oeELt9maK2RyoxhRJ0skZ92Ht0H09xMKhl5IOAt/pub?w=1158&h=744)

# Architecture

At its core Harness is a fast lightweight Server that supplies 2 very flexible APIs and manages Engines. Keep in mind that each type of engines can be used in a mixture or as a single solution. Since the Harness core is lightweight and fast either type of deployment is targeted. Since there are 3 Flavors of Engines here's what they look like in play.  

![Harness Logical Architecture](https://docs.google.com/drawings/d/1SjMDyc16BzHmItpAZuOGIGzbMdlWceK8TM9kde1Ty94/pub?w=908&h=753)

![Harness with Multiple Engines](https://docs.google.com/drawings/d/e/2PACX-1vRVo8cgkxpZGk3LctYttEdTTe0joKIEuFGqlNaZsTExbLmaqPDr7a4jwodHtPKOCgaM7mhhyeLF2H_T/pub?w=1121&h=762)

![Harness Remote Engine Cluster](https://docs.google.com/drawings/d/e/2PACX-1vTjT_hV6pz3Fv5blx9p18NucRXFWzJrHqdG-lDMh7GBYur6JOUcFBgDsh5dJVnhH7JkB2wtB7QewmRK/pub?w=1576&h=1364)

![Harness REST-based Engines](https://docs.google.com/drawings/d/e/2PACX-1vQsBMb7Vg45R9hJg4ZvflamiqzhSkjCk-OZj6GlMzWwnhe-zupCcDrGa5_mzTods6pWsurKOAJEO4mg/pub?w=1051&h=909)

## Harness Core

Harness is a REST server with an API for Engines, Events, Queries, Users, and Permissions. It, in effect proxies the Users and Permissions APIs so as to delegate the logic for dealing with these to the Auth-Server since it is that server's concern to authorize and authenticate all Harness requests.

The Harness Server is also in charge of the CRUD operations on Engines and other administrative operations. It presents the only externally available 
API, which is identical for any Client software, be it an application or the Command Line Interface (CLI), or some future WEB UI.

The Harness server core is small and fast and so is quite useful in single Algorithm solutions but by adding the Auth-server and scalable Engines can also become a SaaS System. 

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints that can be attached at runtime to resource IDs and Engine classes. It is meant as a core piece for HTTP microservices to use in presenting a REST interface and also supports SSL, signature based authentication, and REST route based authorization.

The Router has an API to create endpoints and attach Akka Actors to them for handling incoming requests. This is used to specialize the Router for the work of the particular microservice Engine. The implementaton is based on akka-http and uses the DSL and directives provided by it.

## Administrator

The Administrator executes CRUD type operations on Engines. It will also deal with CLI extensions that are of no concern to the Engines, like status reports, and scheduling of Commands (not implemented yet)

## Templates and Engines

A Template is an Abstract Interface API that needs to be, at least partially implemented by an Engine. They are seen in `com.actionml.core.templates` module. Each Engine must supply required APIs but what they do when invoked is entirely up to the Engine. 

The Template Interface defines and partially implements the APIs for Engine, Dataset, and Algorithm. The Engine class acts as the controller (in the MVC sense) of the Dataset and Algorithm, and the Algorithm manages the model(s). All classes may use the toolset of Harness to ease Engine development, choosing a DB (perhaps MongoDB) a file store (HDFS?) and a compute engine (like Spark). Engines need not be concerned with input and query invocation or dealing with REST APIs since Harness does this.

Engines are instantiated using parameters found in the engine's JSON file, which includes the name of an object with a factory method. This file structure is very flexible and can contain any information the Engine needs to run, including Algorithm Parameters or Compute platform information specific to the Engine.

## The Cluster of Nodes

In V1 Harness is monolithic, requiring one Engine for every EngineId. This scales only vertically of itself and relies on the Engine's use of horizontally scalable services to scale horizontally (much like Apache PredictionIO. To add horizontal scaling to Engines intrinsically Harness V2 will implement an akka Cluster of Nodes, where Engines can be spread out over the cluster. Each Engine will be connected to a akka Event Bus and able to respond to any Event targeted for its EngineId. This is so many Engines may respond to any Event or Query if they are not currently processing one, allowing Engines to scale horizontally of themselves without help from external scalable services.

## Event Bus

The Event Bus presents Events with IDs that are classified to belong to some form of Actor, in Harness this is usually and Engine with a specific 
signature that is derived from the JSON config file, which includes the fartory object, the engines resource id, and all params used in the algorithm. The first available Engine that can respond grabs the Event and processes it by validating it (which may cause an HTTP error status code) and processing it as the Engine sees fit eventually responding with an HTTP code. 

The Event Buss can have multiple Engines that have the same Engine ID and so may share the work of processing the Event. This provides vertical scalability, and the akka Event Bus also provides and error recovery mechanism to handle failures, providing failure resiliency.

The Event Bus is not in Harness V1 but in V2 it is our design goal to provide some form of auto scalability or feedback mechanism where responses for a type of Engine become too slow. 

## Compute Engines

The most obvious and common Compute Engine is Apache Spark but it is the responsibility of the Engine to specify and use the Compute Engine needed. Since Compute Engines are so diverse in APIs and usage no attempt is made by Harness to virtualize these. Typically the Algorithm class will do most interaction with the compute engine but this is not enforced by Harness.

## Mirror Event Store

Events may be any valid form of JSON. We often choose to follow the conventions created by the Apache PredictionIO Project so we can maintain data level compatibility with PIO templates. However due to the fact that Kappa-style online learners do not store events but discard them as their model is updated, Harness provides a method to mirror the eventa for replay while debugging an Engine or while learning to send the right events to the Engine. Engines validate events and will respond with HTTP errors when malformed events come in. This can only be known by the Engine so the raw JSON events are mirrored even if they are not valid. This allows them to be  more easily refined and fixed. Once the events pass the engine's validation this mechanism can also be used as a form of backup for Kappa online learners since playback of all valid events will restore the correct state of the model at the end. 

For Lambda-style learners the mirror plays the same role as a sort of automatic backup, with built-in controls for how many events are preserved in rotation form. in any Lambda system events cannot be allowed to accumulate forever so most Lambda engines implement some form of moving time window (imagine 1 year of events for a big ecommerce site recommender).

# Streaming Online Kappa Learning

The Kappa style learning algorithm takes in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in Harness Templates in [Kappa Learning](kappa-learning.md)

# Batch Offline Lambda Learning

Many algorithms learn by processing a large batch of data and modifying some model all at once but periodically. The Universal Recommender is an example of a Lambda learner. Spark's MLlib also has examples of Lambda style learners. 

# Hybrid Learning

If your Algorithm requires some data (like properties of an object) to be altered in realtime and queries to be based on realtime data but only (re)calculates the model periodically you have a hybrid of Kappa/Lambda learning and the Universal Recommender is an example of this. 
 
# Server REST API

All REST APIs are protected by authorization lists, which specify which routes are accessible to which clients. All APIs will respond with an appropriate HTTP code, some (UPDATE/POST requests) will respond with a JSON  body as described. All data not defined in the URI will be in JSON request and response body.

Integral to REST is the notion of a "resource", which can be though of as a collection of items that can be addressed by a resource-id. Since all REST resource-ids must be URI encoded following the rules for vanilla 
URI fragments when naming resources. The resources defined in Harness are:

 - **engine**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries.
 - **events**: sub-collections that make up a particular dataset used a specific Engine. To send data to an Engine simply `POST /engines/<engine-id>/events/` a JSON Event whose format is defined by the Engine. Non-reserved events (no $ in the name) can be thought of as a unending stream. Reserved eventa like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Engine description for how events are formatted, validated, and processed. 
 - **queries**: queries are made to engines to return information based on their models. since they are completely Engine specific their format, validation, and results are described in the Engine documentation.
 - **commands**: pre-defined commands that perform workflow or administrative tasks. These may be synchronous, returning results with the HTTP response or asynchronous, where they must be polled for status. An example of this is when a command may take long to complete.

For the full Harness REST API and response codes, see the [Harness REST Specification](rest_spec.md)

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending Engine events and making queries. The SDK handles TLS and Authentication, and Authorization based on OAuth2 bearer tokens so it is recommended to use an SDK but since we follow standard OAuth2 one can create their own requests and SDK.

Disregarding the optional TLS and Auth, simple input and queries look like this:

    POST /engines/<engine-id>/events
        Request Body: JSON for event
        Response Body: na
        
    POST /engines/<engine-id>/queries
        Request Body: JSON for query
        Response Body: JSON for results

For specifics of the format and use of events and queries see the Engine documentation. For example the [Contextual Bandit docs](the_contextual_bandit.md). For the full API requests and response codes see the [Harness REST Specification](rest_spec.md)

# [Commands](commands.md)

Commands that do not correspond to an Engine function are REST resources just like Engines so Commands can be fired through REST but we also provide a Command Line Interface (CLI) to allow quick access and control of the server and to support scripted interactions. See [Commands](commands.md)
     
# [Security](security.md)  

Harness optionally supports SSL and Server to Server Authentication. See the [Security](security.md) section for more details.
    
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
