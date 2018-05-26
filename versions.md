# Versions

Harness, in it's early versions, includes SDKs, Auth-Server, REST-Server, and all Engines in source form and is released only as source from it's GitHub repository. This integrated build makes it easy to use with a debugger to create new Engines or modify in other ways.

## v0.2.0-RC1 (work in progress)

 - Refactoring causes the Engine factory names to be of the form `com.actionml.engines...` changing from `org.actionml.templates...`. **NOTE**: this requires that all engine jSON config files to update this factory name or adding the Engine will result in a `timeout` error.
 - Migration to new Mongo Scala Client complete. **NOTE**: a schema change will make the existing DB unusable so they will need to be recreated. Data import compatibility is maintained so if a mirror was kept simply import the saved events to re-create the DB and model.
 - DAO/DAOImpl pattern is used for all DB access, making it easier to port to some other DB store by just creating a new DAOImpl for the new DB.
 - Generic DAO now allows a DAO for any case class using parameterized types.
 - Dependency injection supported for DAO implementation so no code needs to change to support new DB backing Stores.

## v0.1.1

 - The Navigation Hinting Kappa-style Engine is complete in non-personalized form.
 - Integration tests for Nav Hinting added
 - User profiles may be shared between Engine Instances so that the PVR can use both profile and usage data to make better recommendations.
 - Java SDK allows a path to the certificate `.pem` file to be passed in. This is in addition to setting with env or in the source code.

## v0.1.0

 - REST API Complete
 - Java and Python SDKs complete
 - Python CLI Complete
 - Kappa style learning Template API Complete and tested
 - TLS/SSL complete in Server and SDKs
 - Engines include:
    - The Contextual Bandit in a Kappa-style implementation based on the Vowpal Wabbit compute engine
 - Microservice architecture includes HTTP microservices and each Engine is an Actor but does not implement the Event Bus. This makes Harness a non-clustered implementation unless a custom engine uses clustered services like Spark
 - ScalDI injection is supported in limited form, see Engines for examples
 - DAL/DAO/DAO-IMPL is prototyped in the Auth-Server but not generalized for Engine use
 - Realtime Input validation complete
 - Multi-tenancy complete
 - Multiple Permissions complete
 - Compute engine neutrality complete
 - User permission management complete
 - Provisioning is done by hand installation
 - Async APIs for DB access are implemented only in the Auth-Server and the REST API with accompanying SDKs. Many DB access calls in the Engines are synchronous only. 

## Incomplete Features

Based on the [project overview](README.md) the following major features are not implemented unless described in the version details:

 - The Event Bus distributed Engine API, this is expected in v0.2.0.
 - The generic DAL/DAO/DAO-IMPL for the REST Server, this is expected in v0.1.2
 - Containerized Terraformed Provisioning with Orchestration based on Kubernetes, Docker, and Terraform. Delivery TBD