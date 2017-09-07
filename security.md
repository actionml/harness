# Security

Harness optionally uses TLS (formerly called SSL) and OAuth2 "bearer token" Server to Server Authentication to secure both ends of the API. Through TLS the client can trust the server and through OAuth2 the Harness server can authenticate and check for authorization of the client.

## TLS Support

Uses http-akka [server](http://doc.akka.io/docs/akka-http/current/scala/http/server-side/server-https-support.html) and [client](http://doc.akka.io/docs/akka-http/current/scala/http/client-side/client-https-support.html) TLS support.

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

There are 2 role types. In typical use the `client` user-id and secret are used by the SDK on client machines and the admin user-id is used where the CLI is run. Clients have permission to send Events and Queries to specific Engines. Admins have access to all and this is needed to run the CLI.

The see the REST API used to implement User management see the later portion if the [REST Spec](rest_spec.md)

For the CLI that manages users see the [Commands Spec](commands.md)

# Creating Users and Enabling Auth

The typical configuration of Harness is with the Harness and Auth Servers running on the same machine with the CLI installed. Although these can all run on different machines we describe the simple case below.

To create Users the CLI is used and so this is run on the Harness Server. A `client` type user is created for every Engine but only an `admin` user can do this.

 - **`client`**: is used to access engines by sending and events and queries. This is the user role typically used by the Java SDK. The Python SDK can use this too for events and queries
 - **`admin`**: this user role allows superuser type access to all of the REST API and so is only granted to administrators, especially a user of the CLI. Some commands will fail unless the CLI user is an `admin`.

Client users can access only the engine-id they have access to. In this way a multi-tenant setup of Harness will protect one Engine from access by other users. All users (except the admins) are blocked from all Engines unless expressly granted permission when the user-id is created or a later grant of permission is made.

# Auth Setup for Server-side

Creating an `admin` user right after installation can be done with the following steps:

1. Make sure the python-sdk, the auth-server’s, and the Harness server’s auth is disabled (it is disabled by default after installation)

1. Build both Harness and the Auth Server distributions and make sure that the path to both build's `bin/` directories are in the path.
1. **`harness-auth start`** This starts the Auth Server, but does not enable auth. By default the Auth Server runs without auth required. The Auth Server manages users and so can be useful without auth enabled. See instructions below for enabling auth.
2. **`harness start`** This will run harness without auth enabled.
1. **`harness user-add admin`** # this returns user-id and a secret for an `admin` user who can run the CLI when auth is enabled. **Make note of the user-id and secret** they are not stored anywhere by this command and are needed in setup below.
1. **`harness stop`** and **`harness-auth stop`** stop the servers running without auth.
1. Enable auth for the python-sdk, Harness, and the Auth Server by editing `harness-env` in the distribution build and `harness-auth-env/bin/auth-server-env` in the location of your Auth Server build. The auth enabling variable is documented in both files.
 - **Auth Server Auth Enable**: In `harness-auth-env` set the env variable: 
  `export HARNESS_AUTH_SERVER_PROTECTED=true`
  
 - **Harness Auth Enable**: in `harness-env` set:
  `export HARNESS_AUTH_ENABLED=true`
  `export ADMIN_USER_ID=<user-id-returned-when-creating-the-admin-user>`
  `export ADMIN_USER_SECRET_LOCATION=/path/to/admin.secret`
  
      The file referenced by `ADMIN_USER_SECRET_LOCATION` should contain the secret returned when the admin user was created. Best practice is to add this file to the CLI user's `.ssh/` directory and give it the same permission as the ssh private key.
      
 - **CLI Auth Usage**: the CLI uses the Python SDK to access Harness and reads the ENV variables setup above to find credentials to run. No other setup is required.

2. TLS (formerly known as SSL) should be configured for the Harness Server. Instructions TBD.
1. **`harness start`** and **`harness-auth start`** The Harness Server will now require the `admin` user's id and credentials setup in `harness-env`.

**&dagger;**The above process will be wrapped in a shell script that will run all the steps and report user-id and credentials while also configuring the ENV and doing all the start/stop of servers to make it functional. This is not yet available.

## Auth Usage of the Java and Python SDK

The SDK construct `Client` objects like `EventClient` etc. In their constructors each of these objects takes optional credentials for accessing the Engine(s) they have access to. See examples provided with the libraries for specifics.

If the credentials (user-id and secret) are not provided the SDK will communicate without Auth, if they are provided they will send the user-id and secret when needed according to the OAuth2 protocol implemented in the diagram above.

## TLS/SSL for the Client-side

An additional ENV variable must be provided to allow the SDK to trust the cert of the Harness Server. Further instructions TBD.

