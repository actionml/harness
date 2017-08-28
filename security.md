# Security

Harness optionally uses TLS (formerly called SSL) and OAuth2 "bearer token" Server to Server Authentication to secure both ends of the API. Through TLS the client can trust the server and through OAuth2 the Harness server can authenticate and check for authorization of the client.

## TLS Support

Uses [http-akka TLS support](http://doc.akka.io/docs/akka-http/current/scala/http/server-side-https-support.html).

## Authentication

We use "bearer tokens" to authenticate and authorize users. This requires offline exchange of credentials but is secure when combined with TLS/SSL. The user is granted a token that gives them access to Engines on the Harness Server. These tokens are used to construct the SDK clients, which then use them for auth. 

## Authorization

If an authenticated user has been granted access permission for resource, then the request will be processed, otherwise it will be refused with an error code.

# OAuth2

We use OAuth2 "bearer tokens" for Auth. The Bearer Token is used as credentials like a username+password. It is created by the Harness Server, using the Auth-server as a microservice, by an admin user then conveyed to the user of Harness. This allows separation of Engines in a multi-tenant manner with some Engines accessible to some Users and other Engines inaccessible to protect one User's data from another User accessing it.

Some of this data flow is done by humans (the offline part) and some by the SDK to Harness communication (the online part).  

Here is the OAuth2 Bearer Token Protocol mapped onto Harness, the microservice Auth-Server, and the humans involved. 

![](https://docs.google.com/drawings/d/e/2PACX-1vSu_7RpWjYZhhxPfZIvzLfMoCL0traBHs_ATWsEQXeGpYZE6taMMqYFfO-ahcyOQ52Me5zLrTt_tJPM/pub?w=1741&h=2415) 

# Users and Permissions

The see the REST API used to implement User management see the later portion if the [REST Spec](rest_spec.md)

For the CLI that manages users see the [Commands Spec](commands.md)
