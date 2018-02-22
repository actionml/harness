# Versions

Harness, in it's early versions, includes SDKs, Auth-Server, REST-Server, and all Engines in source form and is released only as source from it's GitHub repository. This integrated build makes it easy to use with a debugger to create new Engines or modify in other ways.

## v0.1.1-RC1

 - The Navigation Hinting Kappa-style Template is complete in non-personalized form
 - TTLs are implemented for user click-path journeys have a TTL described in Engines params
 - All User collections, including the shared user collection has a TTL so that inactive users will be aged out of the DB, without this they would need to have `$delete` for each removed User. This still works but is very difficult to coordinate when sharing user data across Engine Instances.
 - The asynchronous MongoDB client is used for all DB access. This improves performance in high throughput cases.
 - DAL/DAO/DAOImpl pattern is used for all DB access, making it easier to port to some other DB store by just creating a new DAOImpl for the new DB.

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