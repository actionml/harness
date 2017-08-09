# Harness Server REST API

REST stands for [REpresentational State Transfer](https://en.wikipedia.org/wiki/Representational_state_transfer) and is a method for identifying resources and operations for be preformed on the with URLs and HTTP verbs. An HTTP POST (now sometimes called UPDATE) corresponds to the U in CRUD, which in turn stands for Create, Update, Read, Delete. So by combining the HTTP verb with a resource identifying URL most desired operations can be constructed. 

There are many cases where these simple methods so not off good ways to encode operations so we do not enforce REST style where is does not fit well. Those places will be noted

# Harness REST

From the outside Harness look like a single server that fields all REST AP
Is, but behind this are serval more heavy-weight services (like databases or compute engines). In cases where Harness needs to define a service we use a ***microservices*** architecture, meaning the service is itself called via HTTP and REST APIs.

In Harness v0.0.1 there is one microservice for Authentication and Authorization, which stores ruleSets for who can access all resources in the system and how they can access, namely which of CRUD they can perform.

# Harness ML/AI REST

Most of the Harness API deals with Engines, which have Events and Queries and Commands, which perform jobs on Harness resources.

| HTTP Verb | URL | Request | Response | Function |
| --- | --- | :---  | :---  | :--- |
| GET | / | none  | JSON describing Harness server status  | Used to get server config information, currently defined Engines, and other pertinent information about the ML/AI operations **minimally implemented** |
| POST | `/engine/<engine-id>` | JSON Engine config | HTTP code, Engine description | Defines an Engine with a resource-id defined, along with Harness and Engine specific config and parameters. See Config for Harness settings, and the Template for Engine params. Optionally depending on the JSON some Engine parts may be modified in place such as Algorithm params modified **(partial update not implemented)** |
| DELETE | `/engine/<engine-id>` | none | HTTP code | Remove and destroy all sub-resources (data and model) and config for the Engine |
| GET | `/engine/<engine-id>` | none | JSON status information about the Engine and sub-resources | Reports Engine status **(not implemented)** |
| GET | `/commands/` | none | JSON listing of active Commands | Some commands are long lived and those still active will have status reported. **(not implemented)** |


        
## REST Endpoints for Lambda Admin (TBD)

in addition to the API above, Lambda style learners require not only setup but batch training. So some additional commands are needed:

| HTTP Verb | URL | Request | Response | Function |
| --- | --- | :---  | :---  | :--- |
| POST | `/commands/batch-train` | JSON params for batch training if defined by the Engine | Resource-id for Command | Used to start a batch training operation for an engine. Supplies any needed identifiers for input and training defined by the Engine **(not implemented)** |
| GET | `/commands/<command-id>` | none | JSON Status of command | Get a report on the progress of an asynchronous long lived command like `batch-train` which may run for hours **(not implemented)** |
| DELETE | `/commands/<command-id>` | none | HTTP code for success or failure | Stop an asynchronous long-lived command **(not implemented)** |

# User Creation And Authorization Rules

These APIs allow the admin user to create new users with access to certain resource types. These APIs act as a thin proxy for communication with the Authe-Server, which implements calls for OAuth2 bearer token Authentication and Authorization.

| HTTP Verb | URL | Request | Response | Function |
| --- | --- | :---  | :---  | :--- |
| POST | `/auth/api/v1/user` | `{“roleSetId”: “client|admin”,` ` “resourceId”: “*|some id”}` | HTTP 200 + `{“bearerToken”: “token”}` or 400 | Create a new user and assign a bearer token, setup internal management of the user-id that does not require saving the bearer token and attached the named `roleSet` for the `resource-id` to the new user |
| DELETE | `/auth/api/v1/user` | `{“roleSetId”: “client|admin”,` `  “resourceId”: "*|<some-user-id>"}` | HTTP 200 and `{“bearerToken”: “token”}` or 400| Removes the user and all access rules from the system |

# Auth-Server REST API

When Harness receives **any** REST API request, the user is authenticated and authorized via the Auth-Server microservice. It uses these calls.

| HTTP Verb | URL | Request | Response | Function |
| --- | --- | :---  | :---  | :--- |
| POST | `/auth/api/v1/authenticate` | `{“token”: “string”}` | HTTP 200 + `{“accessToken”:“string”, “ttl”:<optional long>, “refreshToken”:“optional string”}` or HTTP 401 | authenticates user's access and returns a session token or denies access with HTTP 401 |
| POST | `/auth/api/v1/authorize` | `{“accessToken”:“string”`, ` “role”:”string”`, ` “resourceId”:“string”}` and HTTP 200 or, on failure  HTTP 403 | Given a session/access token authorize the access requested or return an error code |

