# Harness Config

Harness comes with a set of default config details that work for single machine installation. To override these see `rest/server/bin/harness-env.sh` as shown below. 

```
#!/usr/bin/env bash

# Mirroring data is not configured per Engine in the engine.json file used to create the engine
# See examples in rest-server/data/test_resource.json file

# Harness Server config, should work as-id unless you are using SSL
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}
export HARNESS_EXTERNAL_ADDRESS=localhost

# For SSL use the following
# 0.0.0.0 is typical for a server listening for external connections
#export REST_SERVER_HOST=${REST_SERVER_HOST:-0.0.0.0}
# no port changes are required
#export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}
# the HARNESS_EXTERNAL_ADDRESS should be the address your certificate is bound to
#export HARNESS_EXTERNAL_ADDRESS=<external IP address or DNS name>

export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}

export HARNESS_LOG_CONFIG="${HARNESS_HOME}/conf/logback.xml"
export HARNESS_LOG_PATH="${HARNESS_HOME}/logs"

# Harness Auth
export HARNESS_AUTH_ENABLED=${HARNESS_AUTH_ENABLED:-false}
# When auth is enabled there must be an admin user-id set so create one before turning on Auth
# export ADMIN_USER_ID=some-user-id
# Can override where this is stored, the default is where the CLI user's ssh keys are stored
# export ADMIN_USER_SECRET_LOCATION=${ADMIN_USER_SECRET_LOCATION:-"$HOME/.ssh/${ADMIN_USER_ID}.secret"}

# Harness TLS/SSL client support. This is used by the server so any client, including the CLI, should match this setup.
export HARNESS_KEYSTORE_PASSWORD=${HARNESS_KEYSTORE_PASSWORD:-23harness5711!}
export HARNESS_KEYSTORE_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/harness.jks}
export HARNESS_SSL_ENABLED=${HARNESS_SSL_ENABLED:-false}
# Java SDK uses the following, usually set this for any client app using the Java SDK with TLS/SSL
# export HARNESS_SERVER_CERT_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/harness.pem}
```

## Accepting Outside Connections

The first thing to change is that the server must bind to `0.0.0.0` instead of `localhost` if it is to accept connections from other servers.

```
export REST_SERVER_HOST=0.0.0.0
```

## TLS/SSL Support

This needs to be setup for the client and the server. Out of the box Harness requires no changes to use HTTP on localhost:9090. To change this do the following.

### Harness Server TLS/SSL

 - Enable TLS/SSL

    ```
    export HARNESS_SSL_ENABLED=true
    ```

 - Point to the correct Java Key Store file and supply the password for access

    ```
    export HARNESS_SERVER_CERT_PATH=!!path to the .jks file!!
    export HARNESS_KEYSTORE_PASSWORD=!!put your password for the .jks here!!
    ```
        
### Python SDK and CLI

The Python SDK does not need env variables since all parameters for the client are passed in and the CLI reads the same parameters as the server so typically (when running the CLI on the server machine) nothing else needs to be setup

### Java SDK

The Java SDK sets the needed variables in akka-ssl.conf. Out of the box this file is not included in the build of the Java SDK. To enable SSL un-comment `include akka-ssl.conf` in the SDK code. You will find it at `harness/java-sdk/src/main/resources/application.conf`, which is set to say 

```
# include "akka-ssl.conf"
include "akka.conf"
```

by default. Un-comment the include and read `akka-ssl.conf` for how to point to the correct keystore similar to the way the Harness server is setup. 




