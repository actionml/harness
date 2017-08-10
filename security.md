# Security

Harness optionally uses TLS (formerly called SSL) and OAuth2 "bearer token" Server to Server Authentication to secure both ends of the API. Through TLS the client can trust the server and through OAuth2 the Harness server can authenticate and check for authorization of the client.

## TLS Support

Uses [http-akka TLS support](http://doc.akka.io/docs/akka-http/current/scala/http/server-side-https-support.html).

## Authentication

We use "bearer tokens" to authenticate and authorize users. This requires offline exchange of credentials but is secure when combined with TLS/SSL. The user is granted a token that gives them access to Engines on the Harness Server. These tokens are used to construct the SDK clients, which then use them for auth. 

## Authorization

If an authenticated user has been granted access permission for resource, then the request will be processed, otherwise it will be refused with an error code.

# OAuth2

We use OAuth2 "bearer tokens" for Auth. Here is the flow between client and Harness server during Auth. 

![](https://docs.google.com/drawings/d/1_uPiP5UGkphF62PsdIM0Zu8Ugbp4XAKnLYP5CyKrfLo/pub?w=1148&h=572) 

 1. Client sends authorization request to Harness REST server. It directs authorization request to Harness authorization server.
 1. Harness authorization server authenticates REST server via its credentials, searches for authorization rules for that client and REST server and then
 1. generates a resource and sends back to REST server access token for that client
 1. client gets access token
 1. and sends request for protected resource with that access token to Harness REST server
 1. REST server checks access token via authorization server and
 1. sends back protected resource to the client
Authorization Rules
Collection ‘users’ store information about end user’s credentials and roles.
 1. Responce to REST API invocation

```
users: [ // Token holders
  {
    "secret": "bearer token",
    "role": "human readable role id, for example engine.read, engine.modify, resource.read, resource.modify and so on",
    "resourceId": "UUID of the resource, it can also be a wildcard *"
  },
  ...
]
```

Auth-Server REST API

    /POST /auth/api/v1/authenticate
    Request body: {“token”: “string”}
    Response: HTTP code 200 and body {“accessToken”:“string”, “ttl”:<optional long>, “refreshToken”:“optional string”} if authentication was successful; otherwise, HTTP code 401.
    
    /POST /auth/api/v1/authorize
    Request body: {“accessToken”:“string”, “role”:”string”, “resourceId”:“string”}
    Response: HTTP code 200 and body {“success”: “true”} if authorization succeed; otherwise, HTTP code 403.
    
Harness REST API for security management

    /POST /auth/api/v1/user
    Request body: {“roleSetId”: “client|admin”, “resourceId”: “*|some id”}
    Response: HTTP status 200 and body {“bearerToken”: “token”}
    
    /DELETE /auth/api/v1/user
    Request body: {“bearerToken”: “token”}
    Response: HTTP status 200 and body {“success”: “true”}


