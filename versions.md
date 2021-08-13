# Harness Versions

Harness is a complete end-to-end mature Machine Learning server tested in live production deployments. It includes several Engines, most notably, The Universal Recommender. It is designed to allow custom Engines employing flexible learning styles.

## harness-1.0.0-SNAPSHOT (current work-in-progress)

In the "develop" branch of the git repo this will be somewhat unstable at first due to some fundamental changes to synchronizing multiple Harness Servers behind a load balancer. We are also adding the ability to push the "Spark Driver" process into the Spark cluster. These changes are primarily to support very large datasets and very heavy load environments.

## harness-0.7.0 (current stable)

 - Adds features to comply with European GDPR user data privacy laws.
 - Read data for any entity via GET http://<harness-address>/engines/<some-engine-id>/entities/<some-entity-id> This returns date ordered data collected for any entity, which are users in the Universal Recommender.
 - Delete data for any entity via DELETE http://<harness-address>/engines/<some-engine-id>/entities/<some-entity-id> This deletes all data collected for any entity, which are users in the Universal Recommender.
 - Enhanced system status reporting "red" or "green" as well as microservice connection status.

## harness-0.6.2 (current stable)

Bug fix release:

 - Fixes a bug where under some conditions (users with a large history) the most recent user history is truncated when making personalized recs. This only affects personalized recs and is fairly rare.
 - Fixes a problem with dropping unused Elasticsearch indexes after training. This can cause disk to fill with unreachable indexes. Only affects deployments with ES 7.6+, earlier versions of ES do not have this problem.

## harness-0.6.1

Bug fix release:

 - Fixes a bug where moore than 30 items in an itemset query will cause spurious clauses in the ES query and return a server error from Harness/UR
 - Fixes cases where Spark jobs are not marked with the correct status when they abort

## harness-0.6.0

New features include:  

 - Realtime changes to item properties via modifying the model during the $set of properties.

## harness-0.5.1 (containerized)

In production at several locations and in high demand situations. This is the first full containerized release. Improvements include:

 - faster requests per second for input and queries
 - new system status CLI
 - new engine status CLI
 - fully deployed now as images using Docker containers and hosted on hub.docker.com.
 - system setup can be done in docker-compose for development or proof of concept
 - the system can be deployed and managed by Kubernetes. 

We maintain a Kubernetes setup config for all Harness services and operate this in high demand situations as well as smaller deployments. This is not free OSS since it is rather complicated to support but comes with many advantages over source or native installations. 

## harness-0.4.1

Bug fix release.

 - UR engine instances that have no `ttl` defined in the JSON config should get a `"ttl": "365 days"` but in 0.4.0 they get no indexes created. This means queries are relatively slow. This release fixes the problem. In all cases indexes are created and the default `ttl` is 365 days.
 
## harness-0.4.0

- Add the Universal Recommender (UR-0.8.0) ported from PredictionIO's UR-0.7.3
- Minor Universal Recommender feature enhancements
    - Business Rules now called `rules` in queries
    - Event aliases supported to group event types and rearrange via config, requiring no data changes
- greater client support for Elasticsearch 
- refactor the cli into its own repo with the Python SDK
- Creating containers for all services and the CLI
- Support for docker-compose and Kubernetes container orchestration

## harness-0.3.0

 - Add URNavHintingEngine based on the CCO algorithm and the Universal Recommender
 - refactoring into separate repos for the Java SDK and Harness Auth-server
 - CLI for Lambda Engine, like `harness train`

## harness-0.2.0

 - Refactoring causes the Engine factory names to be of the form `com.actionml.engines...` changing from `org.actionml.templates...`. **NOTE**: this requires that all engine jSON config files to update this factory name or adding the Engine will result in a `timeout` error.
 - Migration to new Mongo Scala Client complete. **NOTE**: a schema change will make the existing DB unusable so they will need to be recreated. Data import compatibility is maintained so if a mirror was kept simply import the saved events to re-create the DB and model.
 - DAO/DAOImpl pattern is used for all DB access, making it easier to port to some other DB store by just creating a new DAOImpl for the new DB.
 - Generic DAO now allows a DAO for any case class using parameterized types.
 - Dependency injection supported for DAO implementation so no code needs to change to support new DB backing Stores.

## harness-0.1.1

 - The Navigation Hinting Kappa-style Engine is complete in non-personalized form.
 - Integration tests for Nav Hinting added
 - User profiles may be shared between Engine Instances so that the PVR can use both profile and usage data to make better recommendations.
 - Java SDK allows a path to the certificate `.pem` file to be passed in. This is in addition to setting with env or in the source code.

## harness-0.1.0

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
