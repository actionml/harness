# Harness Design Point

Harness is a server of machine learning engines. Each Engine embodies a machine learning algorithm. It supports both Kappa (online learning) and Lambda (offline or batch learning with real time queries).

See the [README.md](README.md) for a full description

The move from 0.2.0 to 0.3.0 will be the first time we incorporate the Lambda Universal Recommender into Harness. This will require a few features not already built into Harness 0.2.0 and will add some modularity enhancements.

## General Guiding Principles

 - Simplify where possible
 - Harness does only what is requires&mdash;no more
 - Harness is basically a REST Server with TLS and Authentication that proxies input and queries to microservice ML Engines
 - Harness defines the generic interface API for Kappa and Lambda ML Engines
 - Harness also supplies a set of tools like DB access classes and some classes for compute engines like Spark but does not require any of these
 - Harness is designed to run always so Engines can be created, added, deleted, or otherwise managed on demand without the need to restart Harness

## The Harness Server

 - A Secured REST Server that hosts Engine instances
 - it defines the API for ML Engines
 - it contains code to access a shareable persistence resource (DB)
 - Harness contains no state itself but relies on shared resources like DBs to persist state
 - Running 2 instances of Harness on 2 machines that share scalable resources like Spark clusters, DB clusters, or HDFS, is possible
 - Harness manages ML Engines as HTTP based microservices
 - Only the service clients needed by Harness are built into Harness
 - Harness contains the ML Engine proxy code that communicates with the Engine via an HTTP client which is part of the Engine. 

## The Harness Toolbox

Harness only needs to manage microservices so it does not need to directly support compute engines like Spark, or TensorFlow. However several classes are provided as a Toolbox. These classes can be compiled into ML Engines as a convenience. Some of these include:

 - A Store and DAO implementation for Elasticsearch
    - This DAO will have an API that extends the generic interface to support ES specific features
    - This Store and DAO will allow writing an RDD into an index 
 - Spark context access. The various context types supplied by Spark can be created and used.
 - HDFS access used by Spark as I/O
 - A MongoDB Store and DAO implementation that support reading a collection as an RDD or Dataframe used by Spark
 - An Engine stub client in Scala for building into Scala Engines
 - Harness ML Engines are implemented in Docker containers
     - Engines each implement a type of algorithms and reside on one machine identified by a URL+port
     - Engine instances are inside the Engine and are identified by the Engine-id, which is a resource-id in the sense of the Harness REST API. In other words an Engine instance has its own configuration details and is managed by Engine code in one Docker container
     - One Engine (Docker container) can manage an arbitrary number of Engine instances

## Harness Engines

Harness Engines perform some type of ML algorithm and implement the Engine interface API. They use the Harness Toolbox client stub and any other classes there or elsewhere that they need. Though the Harness Toolbox has classes for Spark, MongoDB, and HDFS, Engines are free to use any compute or persistence engine they need.

The requirements for an Engine are:

 - They must implement the Engine API's generic interface
 - They must store no state outside of a sharable persistence engine like a DB or HDFS
 - They must be implemented in a Docker container that can be started by hand at any time before they are instantiated via the Harness CLI or REST API without requiring Harness to be restarted