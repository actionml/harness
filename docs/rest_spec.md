# The Harness REST Specification

REST stands for [REpresentational State Transfer](https://en.wikipedia.org/wiki/Representational_state_transfer) and is a method for identifying resources and operations to be preformed on them. By combining the HTTP verb with a URI most desired operations can be constructed. 

# Harness REST

From the outside Harness looks like a single server that fields all REST APIs, but behind this are serval more heavy-weight services (like databases or compute engines). In cases where Harness needs to define a service we use a ***microservices*** architecture, meaning the service is itself invoked via HTTP APIs and encapsulates some clear function, like the Harness Auth-server. All of these Services and Microservices are invisible to the outside and used only by Harness.

The Harness CLI (`harnessctl`) is implemented in Python as calls to the Harness REST API. See [Commands](commands.md) for more about the CLI.

All input, query, and admin operations are implemented through the REST API. The client application may use one of the provided client SDKs or may use HTTP/HTTPS directly.

# Harness HTTP Response Codes

| HTTP Verb | Collection or item | CRUD | HTTP Code | Meaning |
| --- | --- | :-- | :-- | :-- |
| POST | Collection | Create | 201 | resource created |
| POST | Collection | Create | 202 | a task is accepted for creation |
| POST | Collection | Create | 400 | bad request |
| POST | Item | Create | 201 | resource created |
| POST | Item | Update | 200 | resource updated, via param |
| POST | Item | Create | 404 | resource not found |
| POST | Item | Create | 409 | resource conflict (this is generally allowed by re-defining the resource and so returns 201) |
| GET | Collection or Item | Read | 200 | resource found and returned |
| GET | Collection or Item | Read | 404 | resource not found or invalid |
| PUT | Collection or Item | Update | 405 | resources are created with POST, which may modify also so PUT is not used |
| DELETE | Collection or Item | Delete | 200 | resource deleted |
| DELETE | Collection or Item | Delete | 404 | resource not found or invalid |
| All | Collection or Item | All | 401 | unauthorized request/authroization failure |
| All | Collection or Item | All | 403 | forbidden request/authroization failure |
| All | Collection or Item | All | 405 | method not allowed |

# Harness ML/AI REST

Most of the Harness API deals with Engines, which have sub-resources: Events, Queries, Jobs & others. There are also some REST 
APIs that are administrative in nature. 

# JSON 

Harness uses JSON for all request and response bodies. The format of these bodies are under the control of the Specific Engines with some information layered on by Harness itself.

See the Engine docs for request/response formats. For administrative REST see Harness docs for formats. 

Response and Error codes are defined below.

# Harness REST Types

The Resource-id or type in the REST API follows this model, where Harness owns all resources and each level in the ownership defines which resource own a sub-resource.

![](https://docs.google.com/drawings/d/e/2PACX-1vToTQAtggzYIupQMN6emdlKyqmtXSv1DSM-ZMl2hiAxzxLNAXy3vXCSDrnGoWYZD_YXr2DOc6GIQ6Tg/pub?w=915&h=1007)

Whether the resource definition is sent programmatically or comes from a file (as with an engine's config and params) the actual resource is ALWAYS persisted in a shared store like a DB. Harness itself and all Engines can be expected to be stateless.

## Engines

Each Engine Intance has defining configuration JSON (usually in some file). This config must contain the engine-id. There are other generic params defined in [Harness Config](harness_config.md) but many are Engine specific Algorithm parameters, which are defined by the Engine (the [Universal Recommender](ur_configuration.md) for instance)

## Events

Events encapsulate all input to Engines and are defined by the Engine (the [Universal Recommender](ur_input.md) for example).

## Queries

Queries encapsulate all queries to Engines and are defined by the Engine (the [Universal Recommender](ur_queries.md) for example).

## Jobs

A Job is created to perform some task that may be long lived. For instance to `train` an Engine Instance may take hours. In this case the request creates the job and returns the job-id but this does not mean it is finished. Further status queries for the Engine Instance will report Job status(es)

For example `POST /engines/<some-ur-instance>/jobs` will cause the Universal Recommender to queue up a training Job to be executed on Spark. This POST will have a response with a job-id. This can be used to monitor progress by successive `GET /engines/<some-ur-instance>`, which return job-ids and their status. If it is necessary to abort or cancel a job, just execute `DELETE /engines/<some-ur-instance/jobs/<some-job-id>`. These are more easily performed using the `harnessctl` CLI.

## Users

Users can be created (with the help of the Auth-server) for roles of "client" or "admin". Client Users have CRU access to one or more Engine Instance (only an admin can delete). An admin User has access to all resources and all CRUD. Users are only needed when using the Auth-server's Authentication and Authorization to control access to resources.

# REST API

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| GET | / | none  | See Collection responses | JSON describing Harness server status  | Used to get server config information, currently defined Engines, and other pertinent information about the ML/AI operations **minimally implemented** |
| POST | `/engines/` | JSON Engine config | See Collection responses | Engine description | Defines an Engine with a resource-id in the Request Body config, uses Harness and Engine specific config and parameters. See Config for Harness settings, and the Template for Engine params. Optionally depending on the JSON some Engine parts may be modified in place such as Algorithm params modified **(partial update not implemented)** |
| GET | `/engines/` | none | See Collection responses | Engine descriptions for Engines the user has Read access to | This works like a list command to show all resources the user can read. For the Admin this would show all Engines in the system. **(not implemented)** |
| POST | `/engines/<engine-id>` | JSON Engine config | See Item responses | hint about how to know what was changed | Modify any params that the Engine allows |
| DELETE | `/engines/<engine-id>` | none | See Item responses | none | Remove and destroy all sub-resources (data and model) and config for the Engine |
| GET | `/engines/<engine-id>` | none | See Item responses | JSON status information about the Engine and sub-resources | Reports Engine status |
| POST | `/engines/<engine-id>/events` | none | See Collection responses | JSON event formulated as defined in the Engine docs | Creates an event but may not report its ID since the Event may not be persisted, only used in the algorithm. |
| POST | `/engines/<engine-id>/queries` | none | See Collection responses | JSON query formulated as defined in the Engine docs | Creates a query and returns the result(s) as defined in the Engine docs |
| POST | `/engines/<engine-id>/imports?import_path=<some-path>` | none | 202 "Accepted" or Collection error responses | none |The parameter tells Harness where to import from, see the `harness import` command for the file format |
| POST | `/engines/<engine-id>/configs` | none | See Collection responses | new config to replace the one the Engine is using | Updates different params per Engine type, see Engine docs for details |
| POST | `/engines/<engine-id>/jobs` | JSON params for batch training if defined by the Engine | See Item responses | 202 or collection error responses | Used to start a batch training operation for an engine. Supplies any needed identifiers for input and training defined by the Engine |

# Harness User And Permission APIs

These APIs allow the admin user to create new users granting access to certain resource types. Read no further if you do not use the Auth-server.

These APIs act as a thin proxy for communication with the Auth-Server. They are provided as endpoints on the main Harness rest-server for simplicity but are actually implemented in the Auth-Server microservice. They manage Users by roles and resource-ids.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/users` | `{"roleSetId": "client"|"admin", "resourceId": "some id"}` | See Collection responses | `{"userId": "user_id", “secret”: "token"}` | Create a new user and generate a secret and user-id, setup internal management of the user-id that does not require saving the secret |
| GET | `/users?offset=0&limit=5` | none | see Collection responses |  `[{"userId": "user-id", "roleSetId": "client \| admin", "engines": ["engine-id-1", "engine-id-2", ...]}, ...]` | List all users, roles, and resources they have access to |
| DELETE | `/users/user-id` | none | see Item responses |  `{"userId": "user-id"}` | Delete the User and return their user-id with success. |
| GET | `/users/user-id` | none | see Item responses |  `{"userId": "user-id", "roleSetId": "client" | "admin", "engines": ["engine-id-1", "engine-id-2", ...]}` | List the user's Engines by ID along with the role set they have and potentially other info about the user. |
| POST | `/users/user-id/permissions` | `{"userId": "user-id", "roleSetId": "client\|admin","resourceId": "|<some-engine-id>"}` | See Collection responses |  | Grant named `roleSet` for the `resource-id` to the user with `user-id` |
| DELETE | `/users/user-id/permissions/permission-id` | `{"roleSetId": "client\|admin", "resourceId": "<some-engine-id>"}` | See Item responses | `{"userId": "user_id", "roleSetId": "client\|admin", "resourceId": "<some-engine-id>" }` | Removes a specific permission from a user |

# Auth-Server API (Private)

This API is private and used only by Harness to manage Users and Permissions. It is expected that these resources will be accessed through the Harness API, which will in turn use this API. 

The Auth-Server is a microservice that Harness uses to manage `User` and `Permission` resources. Any holder of a "secret" is a `User` and the `User` may have many permissions, which are the routes and resources they are authorized to access.

The Auth-Server is secured with connection level security no TLS or Auth itself is used. It is expected that the Auth-server runs on tandem to Harness

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/auth/token` | `grant_type=password&username=user-id&password=granted-token`, also app server's credentials should be provided by Authorization Basic header (see [https://tools.ietf.org/html/rfc6749#section-4.3] for details) | 200 or 401 | `{"access_token": "string", "token_type": "", "refresh_token": "optional string"}` | authenticates user's access and returns a session token |
| POST | `/authorize` | `{"accessToken": "string", "roleId": "string", "resourceId": "string"}` | 200 or 403 | `{"success": "true"}` | Given a session/access token authorize the access requested or return an error code |

