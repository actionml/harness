# The Harness REST Specification

REST stands for [REpresentational State Transfer](https://en.wikipedia.org/wiki/Representational_state_transfer) and is a method for identifying resources and operations for be preformed on them with URIs and HTTP verbs. For instance an HTTP POST corresponds to the C in CRUD, which in turn stands for Create, Update, Read, Delete. So by combining the HTTP verb with a resource identifying URi most desired operations can be constructed. 

There are cases where these simple methods do not offer good ways to encode "verb" type operations that are beyond CRUD so we do not enforce REST style where it does not fit well. Those places will be noted.

# Harness REST

From the outside Harness looks like a single server that fields all REST APIs, but behind this are serval more heavy-weight services (like databases or compute engines). In cases where Harness needs to define a service we use a ***microservices*** architecture, meaning the service is itself called via HTTP and REST APIs.

In Harness v0.0.1 there is a microservice for Authentication and Authorization, which manages `users` and `roleSets` that apply to how they can access Harness and all of its resources. Generally these reference resource-ids and routes to the resource as well as which CRUD access they have.

These `roleSets` are defined in the Auth-Server's configuration and are referenced in the REST-API by their names. See the Auth-Server docs for their desciption  (a work in progress), this document describes the entirity of Harness and Auth-Server REST-APIs as designed, though some APIs are not implemented since they are required for operation and those are noted below.

This also does not define the CLI commands that can invoke the APIs&mdash;see [Commands](commands.md) for this.

# Harness HTTP Response Codes

| HTTP Verb | Collection or item | CRUD | HTTP Code | Meaning |
| --- | --- | :-- | :-- | :-- |
| POST | Collection | Create | 201 | resource created |
| POST | Collection | Create | 400 | bad request |
| POST | Item | Create | 201 | resource created |
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

Most of the Harness API deals with Engines, which have Events and Queries, and Commands, which perform jobs on Harness resources.

For "client" type users, POSTing to `/engines/<engine-id>/events` and `/engines/<engine-id>/queries` will be the primary endpoints of interest, the rest typically require "admin" user permissions with some minor exceptions.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| GET | / | none  | See Collection responses | JSON describing Harness server status  | Used to get server config information, currently defined Engines, and other pertinent information about the ML/AI operations **minimally implemented** |
| POST | `/engines/` | JSON Engine config | See Collection responses | Engine description | Defines an Engine with a resource-id in the Request Body config, uses Harness and Engine specific config and parameters. See Config for Harness settings, and the Template for Engine params. Optionally depending on the JSON some Engine parts may be modified in place such as Algorithm params modified **(partial update not implemented)** |
| GET | `/engines/` | none | See Collection responses | Engine descriptions for Engines the user has Read access to | This works like a list command to show all resources the user can read. For the Admin this would show all Engines in the system. **(not implemented)** |
| DELETE | `/engine/<engine-id>` | none | See Item responses | none | Remove and destroy all sub-resources (data and model) and config for the Engine |
| GET | `/engine/<engine-id>` | none | See Item responses | JSON status information about the Engine and sub-resources | Reports Engine status **(not implemented)** |
| POST | `/engine/<engine-id>/events` | none | See Collection responses | JSON event formulated as defined in the Engine docs | Creates an event but may not report its ID since the Event may not be persisted, only used in the algorithm. |
| POST | `/engine/<engine-id>/queries` | none | See Collection responses | JSON query formulated as defined in the Engine docs | Creates a query and returns the result(s) as defined in the Engine docs |
| GET | `/commands/` | none | See Collection responses | JSON listing of active Commands | Some commands are long lived and those still active will have status reported. **(not implemented)** |

        
# Harness Lambda Admin APIs (WIP)

in addition to the API above, Lambda style learners require not only setup but batch training. So some additional commands are needed:

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/commands/batch-train` | JSON params for batch training if defined by the Engine | See Item responses | Resource-id for Command | Used to start a batch training operation for an engine. Supplies any needed identifiers for input and training defined by the Engine **(not implemented)** |
| GET | `/commands/<command-id>` | none | See Item responses | JSON Status of command | Get a report on the progress of an asynchronous long lived command like `batch-train` which may run for hours **(not implemented)** |
| DELETE | `/commands/<command-id>` | none | See Item responses | Response to Command being stopped and removed | Stop an asynchronous long-lived command **(not implemented)** |

# Harness User And Permission APIs

These APIs allow the admin user to create new users granting access to certain resource types.

These APIs act as a thin proxy for communication with the Auth-Server. They are provided as endpoints on the main Harness rest-server for simplicity but are actually implemented in the Auth-Server. Consider these as the public APIs for the Auth-Server. They manage "Users" and "Permissions". The Private part of the Auth-Server deals only with authorization requests and is in the next section.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/users` | `{"roleSetId": "client\|admin", "resourceId": "*\|some id"}` | See Collection responses | `{"userId": "user_id", “bearerToken”: "token"}` | Create a new user and assign a bearer token and user-id, setup internal management of the user-id that does not require saving the bearer token and attached the named `roleSet` for the `resource-id` to the new user |
| GET | `/users` | none | see Collection responses |  `[{"userId": "user-id", "roleSetId": "client \| admin", "engines": ["engine-id-1", "engine-id-2", ...]}, ...]` | List all users, roles, and resources they have access to |
| DELETE | `/users/user-id` | none | see Item responses |  `{"userId": "user-id"}` | Delete the User and return their user-id with success. |
| GET | `/users/user-id` | none | see Item responses |  `{"userId": "user-id", "roleSetId": "client \| admin", "engines": ["engine-id-1", "engine-id-2", ...]}` | List the user's Engines by ID along with the role set they have and potentially other info about the user. |
| POST | `/users/user-id/permissions` | `{"userId": "user-id", "roleSetId": "client\|admin","resourceId": "*\|some id"}` | See Collection responses |  | Grant named `roleSet` for the `resource-id` to the user with `user-id` |
| DELETE | `/users/user-id/permissions/permission-id` | `{"roleSetId": "client\|admin", "resourceId": "*\|<some-engine-id>"}` | See Item responses | `{"userId": "user_id", "roleSetId": "client\|admin", "resourceId": "*\|<some-engine-id>" }` | Removes a specific permission from a user |

# Auth-Server API (Private)

The Auth-Server is a microservice that Harness uses to manage `User` and `Permission` resources. Any holder of a "bearer-token" is a `User` and the `User` may have many permissions, which are the routes and resources they are authorized to access.

The Auth-Server is secured with connection level security no TLS or Auth itself is required and no SDK is provided since only the Harness Rest-Server needs to access it directly.

| HTTP Verb | URL | Request Body | Response Code | Response Body | Function |
| --- | --- | :---  | :---  | :---  | :--- |
| POST | `/auth/token` | `grant_type=password&username=user-id&password=granted-token`, also app server's credentials should be provied by Authorization Basic header (see [https://tools.ietf.org/html/rfc6749#section-4.3] for details) | 200 or 401 | `{"access_token": "string", "token_type": "", "refresh_oken": "optional string"}` | authenticates user's access and returns a session token |
| POST | `/authorize` | `{"accessToken": "string"`, ` "roleId": "string"`, ` "resourceId": "string"}` | 200 or 403 | `{"success": "true"}` | Given a session/access token authorize the access requested or return an error code |

