[![CircleCI](https://circleci.com/gh/actionml/harness.svg?style=svg)](https://circleci.com/gh/actionml/harness)
# Harness Overview

This project implements a microservice based Machine Learning Server. It provides an API for plug-in Engines and implements all services needed for input and query. It is also the platform for the Universal Recommender, which is a Harness Engine.

Harness features include:

## Architecture
 - **Microservice Architecture** Harness uses best-in-class services to achieve scalable performant operation and packages internal systems, where possible as lightweight microservices. 
 - **Single REST API** comes with a Command Line Interface to make admin easier and SDKs for input and queries all accessible through a complete REST API. 
 - **Apache Spark Support** Supports Spark optionally so engines with MLlib algorithms are easy to create and the Universal Recommender Engine uses Spark to create Models. 
 - **Compute-Engine Neutral**: supports any compute engine or pre-packaged algorithm library that is JVM compatible. For example Spark, TensorFlow, Vowpal Wabbit, MLlib, Mahout etc. Does not require Spark, or HDFS but supports them with the Harness Toolbox.

## Operation

 - **Scalability**: to very large datasets via indefinitely scalable compute engines like Spark and databases like MongoDB.
 - **Realtime Input Validation**: Engines supply realtime input validation.
 - **Client Side Support**: SDKs implement support for TLS/SSL and Auth and are designed for asynchronous non-blocking use.
     - **Java SDK**: is supplied for Events and Queries REST endpoints 
     - **Python SDK**: implements client side support for all endpoints and is used by the Python CLI.
     - **REST without SSL and Auth** is simple and can use any HTTP client&mdash;curl, etc. SSL and Auth complicate the client but the API is well specified.
 - **Command Line Interface (CLI)** is implemented as calls to the REST API and so is securely remotable. See the Harness-CLI repo for the Python implementation.
 - **Async SDKs and Internal APIs**: both our outward facing REST APIs as seen from the SDK and most of our internal APIs including those that communicate with Databases are based on Asynchronous/non-blocking IO.
 - **Provisioning/Deployment** can be done by time proven binary installation or using modern container methods:
    - **Containerized** containerized provisioning using Docker and Docker-compose for easy installs or scalable container orchestration
    - **Container Orchestration** ask us about Kubernetes support.
    - **Instance Creation** optional compute and storage resource creation using cloud platform neutral tools like KOPs (Kubernetes for Ops)

## Machine Learning Support
 - **Flexible Learning Styles**:
    - **Kappa**: Online learners that update models in realtime
    - **Lambda**: Batch learners that update or re-compute models in the background during realtime input and queries.
    - **Hybrids**: The Universal Recommender (UR) is an example of a mostly Lambda Engine that will modify some parts of the model in realtime and will use realtime input to make recommendations but the heavy lifting (big data) part of model building is done in the background.
 - **Mutable and Immutable Data Streams**: can coexist in Harness allowing the store to meet the needs of the algorithm.
    - **Realtime Model Updates** if the algorithm allows (as with the Universal Recommender) model updates can be made in realtime. This is accomplished by allowing some or all of the data to affect the model as it it received.
 - **Immutable Data Stream TTLs** even for immutable data streams the 
 - **Data Set Compatibility** with Apache PredictionIO is supported where there is a matching Engine in both systems. For instance `pio export <some-ur-app-id>` produces JSON that can be directly read by the Harness UR via `harness-cli import <some-ur-engine-id> <path-to-pio-export>`. This is gives an easy way to upgrade from PredictionIO to Harness.

## Security

 - **Authentication** support using server to server bearer tokens, similar to basic auth but from the OAuth 2.0 spec. Again on both Server and Client SDKs.
 - **Authorization for Secure Multi-tenancy**: runs multiple Engines with separate Permissions, assign "Client" roles for multiple engines, or root control through the "Admin" role.
     - **Multiple Engine Instances for any Engine Type**: allow any number of variations on one Engine type or multiple Engine types.
     - **Multiple Tenants/Multiple Permissions**: allow user+secret level access control to protect one "tenant" from another. Or Auth can be disabled to allow all Engines access without a user+secret for simple deployments.
 - **TLS/SSL** support from akka-http on the Server as well as in the Java and Python Client SDKs.
 - **User and Permission Management**: Built-in user+secret generation with permissions management at the Engine instance level.
   
# Pre-requisites

In its simplest form Harness has few external pre-requisites. To run it on one machine the requirements are:

## Docker-Compose

The Docker host tools for use with `docker-compose` or run it in a single container, dependent services installed separately. Prerequisites:

 - `nix style Host OS
 - Docker
 - Docker-compose

See the `harness-docker-compose` [project](https://actionml.com/docs/harness_container_guide) for further information.

## Source Build

For installation on a host OS such as an AWS instance without Docker, the minimum requirements are
 
 - Python 3 for CLI and Harness Python SDK
 - Pip 3 to add the SDK to the Python CLI
 - MongoDB 3.x
 - Some recent `nix OS
 - Services used by the Engine of your choice. For instance the UR requires Elasticsearch

Each Engine has its own requirements driven by decisions like what compute engine to use (Spark, TensorFlow, Vowpal Wabbit, DL4J, etc) as well as what Libraries it may need. See specific Engines for their extra requirements. 

# Architecture

At its core Harness is a fast lightweight Server that supplies 2 very flexible APIs and manages Engines. Engines can be used together or as a single solution. 

![Harness with Multiple Engines](https://docs.google.com/drawings/d/e/2PACX-1vTsEtxnVUKnZ6UCoQd9CE7ZSKXqp59Uf9fEtkXJZKtXPFZ1kRrYDnFC-K1y46HTLl5uvXXA-pCZ-ZED/pub?w=1250&h=818)

## Harness Core

Harness is a REST server with an API for Engines, Events, Queries, Users, and Permissions. Events and Queries end-points handle input and queries to Engine instances. The rest of the API is used for administrative functions and is generally accessed through the CLI.

The Harness Server core is small and fast and so is appropriate in single Algorithm solutions&mdash;an instance of The Universal Recommender is a good example. By adding the Harness Auth-server and scalable Engine backend services Harness can also become a full featured multi-tenant SaaS System.  

## Router

The Harness core is made from a component called a Router, which maintains REST endpoints for the various object collections. It routes all requests and responses to/from Engine Instances. It also supports SSL, OAuth2 signature based authentication, and REST route authorization where these are optional.

Algorithm specific Engines can be implemented without the need to deal REST APIs. Engines start by plugging into the abstract Engine API and inherit all server features including administration, input, and query APIs.

## Administrator

The Administrator manages Engine Instances (and optionally Users and Permissions). With release 0.6.0 Harness will support service discovery via integrated Etcd. The Administrator integrates with the Harness Auth Server and Etcd to provide all admin features.

## Engines

An Engine is the implementation of a particular algorithm. They are seen in `com.actionml.core.engines` module. Each Engine must supply required APIs but what they do when invoked is up to the Engine. The format of input and queries is also Engine specific so see the Engine's docs for more info. The inteface for Engines is designed based on years of implementing production worthy ML/AI algorithms and follows patterns that fit about all of the categories of algorithm types.

The common things about all Engines:

 - input is received as JSON "events" from REST POST requests or can be imported from a raw filesystem or the Hadoop Distributed File System (HDFS).
 - queries are JSON from REST POST requests.
 - query results are returned in the response for the request and is defined by the Engine type.
 - have JSON configuration files with generic Engine control params as well as Algorithm specific params. See the Engine docs for Algorithm params and Harness docs for generic Engine params.

## Scaling

Harness is a stateless service. It does very little work itself. It delegates most work to the Engines and other Services that is depends on. These services are all best-in-class made to scale from a shared single machine to massive clusters seamlessly. Harness provides a toolbox of these scalable services that can be used by Engines for state, datasets, and computing.

Harness is scaled by scaling the services it and the various Engines use. For example The Universal Recommender will scale by using more or larger nodes for training (Spark), input storage (MongoDB), and model storage (Elasticsearch). In this manner Harness can be scaled either horizontally (for high availability) or vertically (for cost savings) to supply virtually unlimited resources to an Engine Instance.

## Compute Engines

The use of scalable Compute Engines allow Big Data to be used in Algorithms. For instance one common Compute Engine is Apache Spark, which along with the Hadoop Distributed File System (HDFS) form a massively scalable platform. Spark and HDFS support are provided in the Harness Toolbox.

## Input Store

Due the its extremely flexible data indexing, convenient TTL support, and massive scalability MongoDB is the default data store. 
 
# Server REST API

Harness REST is optionally secured by TLS and Authentication. This requires extensions to the typical REST API to support authentication control objects like Users and Roles, here we ignore these for simplicity. 

Integral to REST is the notion of a "resource", which is an item that can be addressed by a resource-id. POSTing to a resource type creates a single resource. The resource types defined in Harness are:

 - **engines**: the engine is the instance of a Template, with associated knowledge of dataset, parameters, algorithms, models and all needed knowledge to Learn from the dataset to produce a model that will allow the engine to respond to queries. 
     - **events**: sub-collections that make up a particular dataset used a specific Engine. To send data to an Engine simply `POST /engines/<engine-id>/events/` a JSON Event whose format is defined by the Engine. Non-reserved events (no $ in the name) can be thought of as a unending stream. Reserved eventa like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the Engine description for how events are formatted, validated, and processed. 
     - **queries**: queries are created so engines can return information based on their models. See the Engine documentation for their formats.
     - **jobs**: creates a long lived task such as a training task for a Lambda Engine.

For the full Harness REST API and response codes, see the [Harness REST Specification](https://actionml.com/docs/h_rest_spec)

## Input and Query

There are 2 primary APIs in the SDK for sending Engine events and making queries. 

Disregarding the optional TLS and Authentication, simple input and queries for the UR look like this:

**Example Input**

    curl -H "Content-Type: application/json" -d '
    {
       "event" : "buy",
       "entityType" : "user",
       "entityId" : "John Doe",
       "targetEntityType" : "item",
       "targetEntityId" : "iPad",
       "eventTime" : "2019-02-17T21:02:49.228Z"
    }' http://localhost:9090/engines/<some-engine-id>/events

Notice that this posts the JSON body to the `http://localhost:9090/engines/<some-engine-id>/events` endpoint.     

**Example Query**
    
    curl -H "Content-Type: application/json" -d '
    {
      "user": "John Doe"
    }' http://localhost:9090/engines/<some-engine-id>/queries
    
Notice that this posts the JSON body to the `http://localhost:9090/engines/<some-engine-id>/queries` endpoint.         

**Example Query Result**

The result of the query will be in the response body and looks something like this:

    {
        "result":[
            {"item":"Pixel Slate","score":1.4384104013442993},
            {"item":"Surface Pro","score":0.28768208622932434}
        ]
    }


For specifics of the format and use of events and queries see the Engine specific documentation&mdash;for example [The Universal Recommender](https://actionml.com/docs/h_ur). 

# Controlling and Communicating with Harness

 - [The Harness CLI](https://actionml.com/docs/h_commands)

    The Harness server has admin type commands which are used to create and control the workflow of Engines and perform other Admin tasks. This CLI acts as a client to the REST API and so may be run remotely. The project lives in its own [repo here](https://github.com/actionml/harness-cli)
    
 - [The Admin REST API](https://actionml.com/docs/h_rest_spec) A subset of the complete REST API implements all of the functionality of the Harness-CLI and so can be flexibly triggered remotely even without the CLI.
     
 - [Security](https://actionml.com/docs/harness_security)  

    Harness optionally supports SSL and token based Authentication with Authorization.
    
 - [Java Client SDK](https://github.com/actionml/harness-java-sdk/blob/master/README.md)

    The Java SDK is currently source and build instructions. To use this you will include the source and required Java artifacts as shown in the examples then build them into your Java application. Only "Client" role operations are supported bu this SDK; input and query.

- Python Client SDK

    The CLI is implemented using the Python SDK so they are packaged together [here](https://github.com/actionml/harness-cli) 
  
- [Config](https://actionml.com/docs/harness_config.md)

    To change this and other global behavior (not engine specific) read the [Config Page](https://actionml.com/docs/harness_config.md)
    
# Installation

There are several ways to install and run Harness. The primary method we release is through container images but a source project is also maintained for thos who wish to use it.

 - [**Docker-Compose Installation**](https://actionml.com/docs/harness_container_guide) to install an entire system using containers for all services on a single machine,
 - [**Source Build**](https://actionml.com/docs/harness_install)