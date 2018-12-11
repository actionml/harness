# Advanced Settings

This document describes connecting Harness to remote clients, enabling Auth, and SSL for the client SDKs, the Python CLI, and the Harness Server.

## Remote Clients

By default Harness listens on `http://localhost:9090` To have harness listen for connections from outside change the appropriate settings, most importantly:

```
export REST_SERVER_HOST=0.0.0.0 # to listen for external connections
```

## Security

The default Harness install runs out of the box but is without any security, which may be fine for your deployment but if you need to connect over the internet to Harness you will need the Authentication/Authorization Server (Auth Server for short) and TLS/SSL. These 2 parts are independent; Harness uses Auth to trust the client and the client uses TLS to trust the Harness Server and for 

When running all of this on the Server this requires setup of Client and Server side Auth and TLS.

### Server-side Authentication/Authorization

"Auth" starts by creating users see [Commands](commands.md) for User and Permission Management. At minimum you must have an `admin` user to use the CLI. This user can also be used to send test events but typically you will create `client` users for that purpose.

In order to do anything with Users, or Permissions you must start the harness authentication micro-service. 

```
harness-auth start # this is started and stopped like harness
harness user-add admin
```

This will report back a user-id and secret, make note of them. To use the default setup in `bin/harness-env` copy the secret to a file named with the user-id:

    echo <user-secret> > ~/.ssh/<user-id>.secret

This creates a file in the admin user's `.ssh` directory containing the secret. Now all you need to do is open `bin/harness-env` and add the user's id:

    export ADMIN_USER_ID=fc6c8616-1ef8-4440-8875-1bf21d5fbeef
    
When the `admin` user is created a hash of the secret is stored in the Auth-Server DB so the secret is never stored on the server and no reference needs to be made to it. 

When also running a client like a local version of the CLI, it must know the secret and finds it as discussed below.    

### Server-side TLS/SSL Setup

TLS can be toggled via `HARNESS_SSL_ENABLED` env variable (true|false). When set to 'true' it must be enabled on the Harness Server **and** on all SDKs that communicate with it. That means at very least Python must be configured because the CLI uses the Python SDK.

    export HARNESS_SSL_ENABLED=${HARNESS_SSL_ENABLED:-false}
    export HARNESS_KEYSTORE_PASSWORD=${HARNESS_KEYSTORE_PASSWORD:******}
    export HARNESS_KEYSTORE_TYPE=<e.g. JKS(default) or PKCS12>
    export HARNESS_KEYSTORE_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/harness.jks}


### Client-side TLS & Auth

Both the Java and Python SDK (for the CLI too) needs to have the following information for Auth and TLS

 - **user-id**: The user who has been granted permission to access resource(s) on the Harness Server
 - **user-secret**: The secret associated with the user-id above
 - **harness.pem**: The path to a .jks (Java Key Store) formatted certificate created (self-signed) or issued (by a certification authority) to the Harness Server instance used.
 - **URL**: The base URL for the Harness Server. This has always been used but must now the "https" prefix for TLS/SSL where "http" is sufficient for non-SSL.

These can be provided directly to the various SDK APIs but one env setting is needed by the SDKs to point to the `.pem` file

### Client SDK TLS/SSL

To use the SDKs set the following env:


```
export HARNESS_SERVER_CERT_PATH=/home/aml/harness.pem
```

For the Python CLI only you will also need:

```
export HARNESS_EXTERNAL_ADDRESS=1.2.3.4 # use DNS or IP address for cert

```

The server address passed in to the SDKs needs to match the address to which the certificate was granted.

### Client SDK Auth

The user-id and user-secret are passed in to the SDKs and so need to be known to the client.

The Python CLI gets what it needs from the following env vars.

```
# This is needed by the Server so may already be set in harness-env
export ADMIN_USER_ID=fc6c8616-1ef8-4440-8875-1bf21d5fbeef
# This is only used by the Python CLI, to pass in to the Python SDK
export ADMIN_USER_SECRET_LOCATION=${ADMIN_USER_SECRET_LOCATION:-"$HOME/.ssh/${ADMIN_USER_ID}.secret"}

```
