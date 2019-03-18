[![CircleCI](https://circleci.com/gh/actionml/harness.svg?style=svg)](https://circleci.com/gh/actionml/harness)
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
    - **Hybrids**: The Universal Recommender (UR) is an example of a mostly Lambda Engine that will modify some parts of the model in realtime and will use realtime input to make recommendations but the heavy lifting (big data) part of model building is done in the background.
 - **Compute-Engine Neutral**: supports any compute engine or pre-packaged algorithm library that is JVM compatible. For example Spark, TensorFlow, Vowpal Wabbit, MLlib, Mahout etc. Does not require Spark, or HDFS but supports them with the Harness Toolbox.
 - **Scalability**: Engines implemented using either microservice method  and either learning style can be scaled by deploying on multiple nodes. The simplest way to do this is using the Light-weight Actor-based microservice method or by basing the algorithm's implementation on clustered services like Spark.
 - **Scaldi Implementation Injection**:
     - **Main Metadata Store**: is injected at server startup and only supports MongoDB at present.
     - **Input Mirroring**: can be either the server machine's file system or HDFS and the implementation is injected at startup and controlled by configuration. Other mirror store may be supported in the future like Apache Kafka.
     - **DAO/DAL Abstraction**: Virtually any store can be implemented using the DAO/DAL/DAOImpl Pattern (Data Access Layer). There is an example included using MongoDB. 
 - **Realtime Input Validation**: Though optional, Engines are encouraged to supply realtime input validation and a framework based on the Cats library is implemented to support this.
 - **Client Side Support**: As stated above SDKs implement support for TLS/SSL and Auth and are designed for asynchronous non-blocking use for the highest throughput possible (though synchronous blocking use is also supported)
     - **Java SDK**: is supplied for Event and Query endpoints 
     - **Python SDK**: implements client side for all endpoints and is used by the Python CLI.
     - **REST without SSL and Auth** is simple and can use any HTTP client&mdash;curl, etc. SSL and Auth complicate the client but the API is well specified.
 - **Command Line Interface (CLI)** is implemented as calls to the REST API and so is securely remotable. See the Harness-CLI repo for the Python implementation. GUIs based on REST are fairly easy and we plan one for the near future.
 - **Secure Multi-tenancy**: will run multiple Engines with separate Permissions
     - **Multiple Engine-IDs**: allow any number of variations on one Engine type or multiple Engine types. One Engine is the simplest case. By default these all run in a single process and so are lightweight.
     - **Multiple Permissions**: allow user+secret level access control to protect one "tenant" from another. Or Auth can be disabled to allow all Engines access without a user+secret for simple deployments.
 - **Mutable Object and Immutable Event Streams**: can coexist in Harness allowing the store to meet the needs of the algorithm.
 - **User and Permission Management**: Built-in user+secret generation with permissions management at the Engine instance level.
 - **Data Set Compatibility** with Apache PredictionIO is supported where there is a matching Engine in both systems. For instance `pio export <some-ur-engine-id>` can be directly read by the Harness UR via `harness import <some-ur-engine-id> <path-to-pio-export>`. This is an easy way to upgrade from PredictionIO to Harness.
 - **Async SDKs and Internal APIs**: both our outward facing REST APIs as seen from the SDK and most of our internal APIs including those that communicate with Databases are based on Asynchronous/non-blocking usage.
 - **Provisioning** can be done by time proven binary installation or optionally (where ease of deployment, configuration, or scaling is required) using modern container methods:
    - **Containerized** optional containerized provisioning using Docker
    - **Container Orchestration** optional container orchestration with Kubernettes
    - **Instance Creation** optional compute and storage resource creation using cloud platform neutral Terraform

**Note**: not all of the above are implemented in early versions, see [version history](versions.md)  for specifics
 
# Requirements

In its simplest form Harness has few external requirements. To run it on one machine the requirements are:

 - The Docker daemon for Docker installation

For installation on a host OS such as an AWS instance without Docker, the minimum requirements are
 
 - Python 3 for CLI and Harness Python SDK
 - Pip 3 to add the SDK to the Python CLI
 - MongoDB 3.x (pluggable via a clean DAO/DAL interface with injected DAOImpl for any DB)
 - Some recent `nix OS
 - Services used by the Engine of your choice. For instance the UR requires Elasticsearch

Each Engine has its own requirements driven by decisions like what compute engine to use (Spark, TensorFlow, Vowpal Wabbit, DL4J, etc) as well as what Libraries it may need. See specific Engines for their extra requirements. 

# Microservices

There are 3 types of Engine microservices in Harness:

 1. **In-Process Multithreaded Engine**: these are implemented in a lightweight performant way that can scale by using scalable Services like Spark for computing and Hadoop's HDFS for storage
 2. **Remote akka Actor-based Engine** We use an akka System of Actors spanning multiple nodes attached to an akka Event Bus. This type of engine can be scaled by adding multiple identical Engines to the Event bus all processing a single input or request then waiting for more. This also allows for the use of scalable services like Elasticsearch for K-Nearest Neighbor serving of Spark for computing. Coming in Harness 0.5+
 3. **Remote REST-based Engine**: Engines may be deployed as separate REST-microservices in containers or on separate machines. This allows microservice Engines to be developed in any language that can have a REST-based "Client" to the Harness Engine REST-based Proxy. We plan to support Python with a Proxy and Client so Engines can be in Python, but any language that can support a REST client and JSON data exchange can be used to implement Engines in this fashion. Coming in Harness 0.5+

![Harness Engine Flavors](https://docs.google.com/drawings/d/e/2PACX-1vSbM2ZDzUJ9JPcOODKpmvuB5F1giDNM4lGFhP5E_oeELt9maK2RyoxhRJ0skZ92Ht0H09xMKhl5IOAt/pub?w=1158&h=744)

# Architecture

At its core Harness is a fast lightweight Server that supplies 2 very flexible APIs and manages Engines. Keep in mind that each type of engines can be used in a mixture or as a single solution. Since the Harness core is lightweight and fast either type of deployment is targeted equally well. 

![Harness with Multiple Engines](https://docs.google.com/drawings/d/e/2PACX-1vRVo8cgkxpZGk3LctYttEdTTe0joKIEuFGqlNaZsTExbLmaqPDr7a4jwodHtPKOCgaM7mhhyeLF2H_T/pub?w=1121&h=762)

## Harness Core

Harness is a REST server with an API for Engines, Events, Queries, Users, and Permissions. Events and Queries end-points handle input and queries to Engine instances. The rest of the API is used for administration type functions and is generally used by the CLI or a future GUI.

The Harness Server core is small and fast and so is appropriate in single Algorithm solutions&mdash;an instance of The Universal Recommender is a good example. By adding the Harness Auth-server and scalable Engine backend services Harness can also become a full features Multi-tenant SaaS System.  

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints for the various object collections. It is meant as a core piece for HTTP microservices to use in presenting a REST interface and also supports SSL, OAuth2 signature based authentication, and REST route authorization.

Algorithm specific Engines can be implemented without the need to deal REST APIs. Engines start by plugging into the abstract Engine API and inherit all server features including administration, input, and query APIs.

The Router has a admin API to create instances of Engines and attach them to REST endpoints.

## Administrator

The Administrator executes CRUD type operations on Engines. It will also (optionally) deals with administration type CLI extensions, which create Users, and assign Permissions based on a simple role-set. 

## Engines

An Engine is the implementation of a particular algorithm. They are seen in `com.actionml.core.engines` module. Each Engine must supply required APIs but what they do when invoked is entirely up to the Engine since they are algorithm specific. The format of input and queries is also Engine specific so see the Engine's docs for more info.

The common things about all Engines:

 - input is received as JSON "events" from the REST POST requests.
 - queries are JSON from the REST POST requests.
 - query results are returned in the response for the request
 - have JSON configuration files with generic Engine control params as well as Algorithm params. See the Engine docs for Algorithm params and Harness docs for generic Engine params.

## Scaling

Harness is a stateless service. It does very little work itself. It delegates most work to the Engines, which are scaled in their own manner. Harness provides a toolbox of scalable services that can be used for state and datasets.

Harness is scaled by scaling the services it and the various Engines use. For example The Universal Recommender will scale by using more Spark nodes for training, more Mongo nodes for storing input, and more Elasticsearch nodes for larger models. This scaling allows  virtually unlimited resources to be applied to an Engine Instance.

## Compute Engines

The use of scalable Compute Engines allow Big Data to be used in Algorithms. The most obvious and common Compute Engine is Apache Spark, which along with the Hadoop Distributed File System (HDFS) form a massively scalable platform. Spark and HDFS support are provided (along with Elasticsearch) in the Harness Toolbox.

However an Engine can choose to use other Compute Engines if it fits the Algorithm better. For instance Tensor Flow or DL4J for Neural Nets.

# Streaming Online Kappa Learning

The Kappa style learning algorithm take in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in Harness Templates in [Kappa Learning](kappa-learning.md)

# Batch Offline Lambda Learning

Many algorithms learn by processing a large batch of data and modifying some model all at once but periodically. The Universal Recommender is an example of a Lambda learner. Spark's MLlib also has examples of Lambda style learners. 

# Hybrid Learning

The [Universal Recommender (UR)](ur_simple_usage.md) is an example of a hybrid learner. It calculates the largest part of its model in the background using Spark and Apache Mahout. This model can itself be modified in realtime by some input, for instance item properties, but the entire is meant to be re-calculated periodically. The UR is also able to use realtime user behavior to make personalized recommendations. 
 
# Server REST API

Harness REST is optionally secured by TLS and Authentication. This requires extensions to the typical REST API to support authentication control objects like Users and Roles, here we ignore these for simplicity. 

Integral to REST is the notion of a "resource", which is an item that can be addressed by a resource-id. POSTing to a resource type creates a single resource. The resource types defined in Harness are:

 - **engines**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries.

 sub-types representing child collections are:
 
     - **events**: sub-collections that make up a particular dataset used a specific Engine. To send data to an Engine simply `POST /engines/<engine-id>/events/` a JSON Event whose format is defined by the Engine. Non-reserved events (no $ in the name) can be thought of as a unending stream. Reserved eventa like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Engine description for how events are formatted, validated, and processed. 
     - **queries**: queries are created so engines can return information based on their models. See the Engine documentation for their formats.
     - **jobs**: creates a long lived task such as a training task for a Lambda Engine.

For the full Harness REST API and response codes, see the [Harness REST Specification](rest_spec.md)

## Input and Query

There are 2 primary APIs in the SDK for sending Engine events and making queries. 

Disregarding the optional TLS and Authentication, simple input and queries for the UR look like this:

    curl -H "Content-Type: application/json" -d '
    {
       "event" : "buy",
       "entityType" : "user",
       "entityId" : "John Doe",
       "targetEntityType" : "item",
       "targetEntityId" : "iPad",
       "eventTime" : "2019-02-17T21:02:49.228Z"
    }' http://localhost:9090/engines/<some-engine-id>/events
        
    
    curl -H "Content-Type: application/json" -d '
    {
      "user": "John Doe"
    }' http://localhost:9090/engines/<some-engine-id>/queries

The result of the query will be in the response body and look something like this:

    {
        "result":[
            {"item":"Pixel Slate","score":1.4384104013442993},
            {"item":"Surface Pro","score":0.28768208622932434}
        ]
    }


For specifics of the format and use of events and queries see the Engine specific documentation&mdash;for example [The Universal Recommender](ur_simple_usage.md). 

# Controlling and Communicating with Harness

 - [The Harness CLI](commands.md)

    The Harness server has admin type commands which are used to create and control the workflow of Engines and perform other admin tasks. This CLI acts as a client to the REST API and so may be run remotely. The project lives in its own [repo here](https://github.com/actionml/harness-cli) See [Commands](commands.md) for a more detailed description.
     
 - [Security](security.md)  

    Harness optionally supports SSL and token based Authentication. See the [Security](security.md) section for more details.
    
 - [Java SDK](java-sdk.md)

    The Java SDK is currently source and build instructions. You must include the source and required Java artifacts as shown in the examples then build them into your Java application.

- [Python SDK](commands.md)

    The CLI is implemented using the Python SDK and is packaged along with the [CLI here](https://github.com/actionml/harness-cli) 
  
- [Config](harness_config.md)

    Out of the box Harness runs on localhost and does not mirror events. To change this and other global behavior (not engine specific) read the [Config Page](harness_config.md)
    
# Installation

There are several ways to install and run Harness. The most common, which may not be the easiest, is described in the [Install section](intall.md). This is a guide to installation on the OS of a server. 
