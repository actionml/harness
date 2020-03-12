# Versions

Harness is a complete end-to-end Machine Learning and Artificial Intelligence server in early maturity. Meaning all minimum viable product features are included and tested in production deployments. It includes several Engines, most notably The Universal Recommender. It is designed to allow custom Engines employing flexible learning styles.

## 0.5.1 (current stable containerized)

In production at several locations and in high demand situations. This is the first full containerized release. Improvements include:

 - faster requests per second for input and queries
 - new system status CLI
 - new engine status CLI
 - fully deployed now as images using Docker containers and hosted on hub.docker.com.
 - system setup can be done in docker-compose for development or proof of concept
 - the system can be deployed and managed by Kubernetes. 

We maintain a Kubernetes setup config for all Harness services and operate this in high demand situations as well as smaller deployments. This is not free OSS since it is rather complicated to support but comes with many advantages over source or native installations. 


### UR-0.9.0

 - improved requests per second for input and queries
 - Elasticsearch 6.x and 7 support
 - ES 7 has scoring differences and the integration test has not been updated to reflect these so do not trust the test on ES 7
 - Realtime property changes for $set, no need to re-train to have them updated in the model
 - Background index updates for Mongo when changing the TTL in a dataset
 - New statuses for long lived Jobs, these are now persistent over a restart of Harness. Spark Jobs now have a "completed" or "failed" status.
 - Extended JSON for Engine Instance Config files, which pulls values from env.

## 0.4.1

Bug fix release.

 - UR engine instances that have no `ttl` defined in the JSON config should get a `"ttl": "365 days"` but in 0.4.0 they get no indexes created. This means queries are relatively slow. This release fixes the problem. In all cases indexes are created and the default `ttl` is 365 days.
 
## 0.4.0

- Add the Universal Recommender (UR-0.8.0) ported from PredictionIO's UR-0.7.3
- Minor Universal Recommender feature enhancements
    - Business Rules now called `rules` in queries
    - Event aliases supported to group event types and rearrange via config, requiring no data changes
- greater client support for Elasticsearch 
- refactor the cli into its own repo with the Python SDK
- Creating containers for all services and the CLI
- Support for docker-compose and Kubernetes container orchestration

## 0.3.0

 - Add URNavHintingEngine based on the CCO algorithm and the Universal Recommender
 - refactoring into separate repos for the Java SDK and Harness Auth-server
 - CLI for Lambda Engine, like `harness train`

## v0.2.0

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
