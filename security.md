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

The see the REST API used to implement User management see the later portion if the [REST Spec](rest_spec.md)

For the CLI that manages users see the [Commands Spec](commands.md)

## Create Admin User and Enabling Auth

The typical configuration of Harness is with the Harness and Auth Servers running on the same machine with the CLI installed. Although these can all run on different machines we describe the simple case here:

Creating admin right after installation can be done with the following steps:

1. Make sure the python-sdk, the auth-server’s, and the Harness server’s auth is disabled (it is disabled by default after installation)

1. After building both Harness and the Auth Server make suer that the path to both distribution build's `bin/` directories are in the path.
1. **`harness-auth-start`** Normally the auth-server is only started when harness is using auth this is the only case it is needed without auth enabled.
2. **`harness start`**
1. **`harness user-add admin`** # this returns user-id and a secret
1. **`harness stop`** and **`auth-server stop`**
1. Enable auth for the python-sdk, Harness, and the Auth Server by editing `harness/bin/harness-env` in the distrubution built using `make-distribution.sh` and `auth-server/bin/auth-server-env` in the location of your Auth Server build. The auth enabling variable is documented in both files.
 - **The user-id** for the admin can be in the ENV in clear text. 
 - **The "secret"** used as the OAuth2 Bearer Token should have an ENV variable that points to a file with read/write permission only for the linux user running harness, much like the permissions used for ssh private keys. The secret should be in a file in ~/.ssh/harness-user-key with RW permission only for the user of the SDK or the user who has lunched Harness. This mechanism is used on the client-side with the Java or Python SDK as well as the CLI (which uses the Python SDK).
1. Once the ENV is setup, the user-id and secret are stored securely the CLI will use these to access Harness when auth is enabled.
2. Set ENV in both servers `-env` files to require TLS.
1. `harness start` and `auth-server start` these will now require a user-id and credentials for any access since all resources are protected. These have been setup in the ENV in step #6.

**&dagger;**The above process will be wrapped in a `setup-auth.sh` script that will run all the steps and report user-id and credentials while also configuring the ENV and doing all the start/stop of servers to make it functional. This is not yet available. **Note**: setting up a remote admin on another machine who connects to Harness across the internet securely is quite possible but the above steps should be executed on the 2 different machines and will not be scripted.

