# Harness Server REST API

REST stands for [REpresentational State Transfer](https://en.wikipedia.org/wiki/Representational_state_transfer) and is a method for identifying resources and operations for be preformed on the with URLs and HTTP verbs. An HTTP POST corresponds to the U in CRUD, which in turn stands for Create, Update, Read, Delete. So by combining the HTTP verb with a resource identifying URL most desired operations can be constructed. 

There are many cases where these simple methods so not off good ways to encode operations so we do not enforce REST style where is does not fit well. Those places will be noted

# Harness REST

From the outside Harness look like a single server that fields all REST AP
Is, but behind this are serval more heavy-weight services (like databases or compute engines). In cases where Harness needs to define a service we use a ***microservices*** architecture, meaning the service is itself called via HTTP and REST APIs.

In Harness v0.0.1 there is one microservice for Authentication and Authorization, which stores ruleSets for who can access all resources in the system and how they can access, namely which of CRUD they can perform.

# Harness ML/AI REST

Most of the Harness API deals with Engines, which have Events and Queries and Commands, which perform jobs on Harness resources.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| GET | / | none  | 200 or 400 | JSON describing Harness server status  | Used to get server config information, currently defined Engines, and other pertinent information about the ML/AI operations **minimally implemented** |
| POST | `/engines/` | JSON Engine config | 200 or 400 | Engine description | Defines an Engine with a resource-id in the Request Body config, uses Harness and Engine specific config and parameters. See Config for Harness settings, and the Template for Engine params. Optionally depending on the JSON some Engine parts may be modified in place such as Algorithm params modified **(partial update not implemented)** |
| DELETE | `/engine/<engine-id>` | none | 200 or 400 | none | Remove and destroy all sub-resources (data and model) and config for the Engine |
| GET | `/engine/<engine-id>` | none | 200 or 400 | JSON status information about the Engine and sub-resources | Reports Engine status **(not implemented)** |
| GET | `/commands/` | none | 200 or 400 | JSON listing of active Commands | Some commands are long lived and those still active will have status reported. **(not implemented)** |


        
## REST Endpoints for Lambda Admin (TBD)

in addition to the API above, Lambda style learners require not only setup but batch training. So some additional commands are needed:

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/commands/batch-train` | JSON params for batch training if defined by the Engine | 200 or 400| Resource-id for Command | Used to start a batch training operation for an engine. Supplies any needed identifiers for input and training defined by the Engine **(not implemented)** |
| GET | `/commands/<command-id>` | none | 200 or 400 | JSON Status of command | Get a report on the progress of an asynchronous long lived command like `batch-train` which may run for hours **(not implemented)** |
| DELETE | `/commands/<command-id>` | none | 200 or 400 | Response to Command being stopped and removed | Stop an asynchronous long-lived command **(not implemented)** |

# User Creation And Authorization Rules

These APIs allow the admin user to create new users with access to certain resource types. These APIs act as a thin proxy for communication with the Authe-Server, which implements calls for OAuth2 bearer token Authentication and Authorization.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/users` | `{"roleSetId": "client\|admin","resourceId": "*\|some id"}` | 200 or 400 | `{“bearerToken”: "token"}` | Create a new user and assign a bearer token, setup internal management of the user-id that does not require saving the bearer token and attached the named `roleSet` for the `resource-id` to the new user |
| DELETE | `/users/user-id` | `{"roleSetId": "client|admin", "resourceId": "*|<some-user-id>"}` | 200 or 400 | `{"bearerToken": "token"}` | Removes the user and all access rules from the system |

# Auth-Server REST API (Private)

The Auth-Server is a microservice that Harness uses to manages `User` resources and the routes and resoures they are authorized to access. It is secured with connection security no TLS or Auth itself is required and no client is provided since the users need neve access it direclty. **This section is under construction.**

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/authenticate` | `{"token": "string"}` | 200 or 401 | `{"accessToken": "string", "ttl": <optional long>, "refreshToken": "optional string"}` | authenticates user's access and returns a session token or denies access with HTTP 401 |
| POST | `/authorize` | `{"accessToken": "string"`, ` "role": "string"`, ` "resourceId": "string"}` | 200 or 403 | `{"success": "true"}` if authorization succeed; otherwise, HTTP 403 | Given a session/access token authorize the access requested or return an error code |

