[![CircleCI](https://circleci.com/gh/actionml/harness.svg?style=svg)](https://circleci.com/gh/actionml/harness)
# Harness Overview

This project implements a microservice based Machine Learning Server. It provides an API for plug-in Engines and implements all services needed for input and query. Features include:

 - **TLS/SSL** support from akka-http on the Server as well as in the Java and Python Client SDKs
 - **Authentication** support using server to server bearer tokens, similar to basic auth but from the OAuth 2.0 spec. Again on both Server and Client SDKs.
 - **Authorization** based on Roles and Permissions for secure multi-tenancy
 - **Microservice Architecture**
 - **Single REST API** for input, query, and admin type commands
 - **Flexible Learning Styles**:
    - **Kappa**: Online learners that update models in realtime
    - **Lambda**: Batch learners that update or re-compute models in the background during realtime input and queries.
    - **Hybrids**: The Universal Recommender (UR) is an example of a mostly Lambda Engine that will modify some parts of the model in realtime and will use realtime input to make recommendations but the heavy lifting (big data) part of model building is done in the background.
 - **Compute-Engine Neutral**: supports any compute engine or pre-packaged algorithm library that is JVM compatible. For example Spark, TensorFlow, Vowpal Wabbit, MLlib, Mahout etc. Does not require Spark, or HDFS but supports them with the Harness Toolbox.
 - **Scalability**: to very large datasets via indefinitely scalable compute engines like Spark and databases like MongoDB.
 - **Realtime Input Validation**: Engines supply realtime input validation.
 - **Client Side Support**: SDKs implement support for TLS/SSL and Auth and are designed for asynchronous non-blocking use.
     - **Java SDK**: is supplied for Events and Queries REST endpoints 
     - **Python SDK**: implements client side support for all endpoints and is used by the Python CLI.
     - **REST without SSL and Auth** is simple and can use any HTTP client&mdash;curl, etc. SSL and Auth complicate the client but the API is well specified.
 - **Command Line Interface (CLI)** is implemented as calls to the REST API and so is securely remotable. See the Harness-CLI repo for the Python implementation.
 - **Authorization for Secure Multi-tenancy**: runs multiple Engines with separate Permissions
     - **Multiple Engine-IDs**: allow any number of variations on one Engine type or multiple Engine types. One Engine is the simplest case. By default these all run in a single process and so are lightweight.
     - **Multiple Permissions**: allow user+secret level access control to protect one "tenant" from another. Or Auth can be disabled to allow all Engines access without a user+secret for simple deployments.
 - **Mutable Object and Immutable Event Streams**: can coexist in Harness allowing the store to meet the needs of the algorithm.
 - **User and Permission Management**: Built-in user+secret generation with permissions management at the Engine instance level.
 - **Data Set Compatibility** with Apache PredictionIO is supported where there is a matching Engine in both systems. For instance `pio export <some-ur-app-id>` produces JSON that can be directly read by the Harness UR via `harness import <some-ur-engine-id> <path-to-pio-export>`. This is gives an easy way to upgrade from PredictionIO to Harness.
 - **Async SDKs and Internal APIs**: both our outward facing REST APIs as seen from the SDK and most of our internal APIs including those that communicate with Databases are based on Asynchronous/non-blocking IO.
 - **Provisioning/Deployment** can be done by time proven binary installation or using modern container methods:
    - **Containerized** containerized provisioning using Docker and Docker-compose for easy installs or scalable container orchestration
    - **Container Orchestration** ask us about Kubernetes support.
    - **Instance Creation** optional compute and storage resource creation using cloud platform neutral tools like KOPs (Kubernetes for Ops)

See [version history](versions.md)  for specifics
 
# Pre-requisites

In its simplest form Harness has few external pre-requisites. To run it on one machine the requirements are:

 - The Docker daemon for Docker installation

For installation on a host OS such as an AWS instance without Docker, the minimum requirements are
 
 - Python 3 for CLI and Harness Python SDK
 - Pip 3 to add the SDK to the Python CLI
 - MongoDB 3.x (pluggable via a clean DAO/DAL interface with injected DAOImpl for any DB)
 - Some recent `nix OS
 - Services used by the Engine of your choice. For instance the UR requires Elasticsearch

Each Engine has its own requirements driven by decisions like what compute engine to use (Spark, TensorFlow, Vowpal Wabbit, DL4J, etc) as well as what Libraries it may need. See specific Engines for their extra requirements. 

# Architecture

At its core Harness is a fast lightweight Server that supplies 2 very flexible APIs and manages Engines. Each type of engines can be used together or as a single solution. 

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

An Engine is the implementation of a particular algorithm. They are seen in `com.actionml.core.engines` module. Each Engine must supply required APIs but what they do when invoked is entirely up to the Engine. The format of input and queries is also Engine specific so see the Engine's docs for more info.

The common things about all Engines:

 - input is received as JSON "events" from REST POST requests.
 - queries are JSON from REST POST requests.
 - query results are returned in the response for the request
 - have JSON configuration files with generic Engine control params as well as Algorithm params. See the Engine docs for Algorithm params and Harness docs for generic Engine params.

## Scaling

Harness is a stateless service. It does very little work itself. It delegates most work to the Engines, which are scaled in their own manner. Harness provides a toolbox of scalable services that can be used for state, datasets, and computing.

Harness is scaled by scaling the services it and the various Engines use. For example The Universal Recommender will scale by using more Spark nodes for training, more Mongo nodes for storing input, and more Elasticsearch nodes for larger models. This scaling allows  virtually unlimited resources to be applied to an Engine Instance.

## Compute Engines

The use of scalable Compute Engines allow Big Data to be used in Algorithms. The most obvious and common Compute Engine is Apache Spark, which along with the Hadoop Distributed File System (HDFS) form a massively scalable platform. Spark and HDFS support are provided (along with Elasticsearch) in the Harness Toolbox.

# Streaming Online Kappa Learning

The Kappa style learning algorithm take in unbounded streams of data and incrementally updates the model without the need for a background batch operation. See the discussion of how this works in Harness Templates in [Kappa Learning](docs/kappa-learning.md)

# Batch Offline Lambda Learning

Many algorithms learn by processing a large batch of data and modifying some model all at once but periodically. The Universal Recommender is an example of a Lambda learner. Spark's MLlib also has examples of Lambda style learners. 

# Hybrid Learning

The [Universal Recommender (UR)](docs/ur_simple_usage.md) is an example of a hybrid learner. It calculates the largest part of its model in the background using Spark and Apache Mahout. This model can itself be modified in realtime by some input, for instance item properties, but the entire is meant to be re-calculated periodically. The UR is also able to use realtime user behavior to make personalized recommendations. 
 
# Server REST API

Harness REST is optionally secured by TLS and Authentication. This requires extensions to the typical REST API to support authentication control objects like Users and Roles, here we ignore these for simplicity. 

Integral to REST is the notion of a "resource", which is an item that can be addressed by a resource-id. POSTing to a resource type creates a single resource. The resource types defined in Harness are:

 - **engines**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries. 
     - **events**: sub-collections that make up a particular dataset used a specific Engine. To send data to an Engine simply `POST /engines/<engine-id>/events/` a JSON Event whose format is defined by the Engine. Non-reserved events (no $ in the name) can be thought of as a unending stream. Reserved eventa like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Engine description for how events are formatted, validated, and processed. 
     - **queries**: queries are created so engines can return information based on their models. See the Engine documentation for their formats.
     - **jobs**: creates a long lived task such as a training task for a Lambda Engine.

For the full Harness REST API and response codes, see the [Harness REST Specification](docs/rest_spec.md)

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

The result of the query will be in the response body and looks like this:

    {
        "result":[
            {"item":"Pixel Slate","score":1.4384104013442993},
            {"item":"Surface Pro","score":0.28768208622932434}
        ]
    }


For specifics of the format and use of events and queries see the Engine specific documentation&mdash;for example [The Universal Recommender](docs/ur_simple_usage.md). 

# Controlling and Communicating with Harness

 - [The Harness CLI](docs/commands.md)

    The Harness server has admin type commands which are used to create and control the workflow of Engines and perform other admin tasks. This CLI acts as a client to the REST API and so may be run remotely. The project lives in its own [repo here](https://github.com/actionml/harness-cli) See [Commands](commands.md) for a more detailed description.
     
 - [Security](docs/security.md)  

    Harness optionally supports SSL and token based Authentication. See the [Security](security.md) section for more details.
    
 - [Java Client SDK](https://github.com/actionml/harness-java-sdk/blob/master/README.md)

    The Java SDK is currently source and build instructions. You must include the source and required Java artifacts as shown in the examples then build them into your Java application.

- Python Client SDK

    The CLI is implemented using the Python SDK and is packaged along with the [CLI here](https://github.com/actionml/harness-cli) 
  
- [Config](docs/harness_config.md)

    Out of the box Harness runs on localhost and does not mirror events. To change this and other global behavior (not engine specific) read the [Config Page](docs/harness_config.md)
    
# Installation

There are several ways to install and run Harness. The most common, which may not be the easiest, is described in the [Install section](docs/install.md). This is a guide to installation on the OS of a server. 
