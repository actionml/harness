# Harness Config

Harness config come in two parts:

 - **Server config**: Defines server settings, how it connects to other services or to applications, security, and other server specific settings. These settings are collected in `harness-env` in the form of environment variables.
 - **Engine config**: Defines a set of parameters than are available to all engine instances via their JSON config files. These include method and location of event mirroring, model storage, factory object name, and other parameters that apply to a specific engine instance but is available to all engines. 

## Harness Server Config

Harness server settings are in `rest/server/bin/harness-env.sh` as shown below. 

```
#!/usr/bin/env bash

# Harness Server config, should work as-id unless you are using SSL
# to listen on the host IP address for external connections
# export REST_SERVER_HOST=0.0.0.0
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
# to change the port used
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}
# to change the host address
export HARNESS_EXTERNAL_ADDRESS=localhost

# To connect to a required MongoDB instance or cluster
export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}

# To configure Harness logging
export HARNESS_LOG_CONFIG="${HARNESS_HOME}/conf/logback.xml"
export HARNESS_LOG_PATH="${HARNESS_HOME}/logs"

# =============================================================
# Read no further if you do not need TLS/SSL or Authentication
# =============================================================

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

The Python SDK does not need env variables since all parameters for the client are passed in.

The CLI reads the same parameters as the server so typically (when running the CLI on the server machine) only the extra:

```
HARNESS_EXTERNAL_ADDRESS=1.2.3.4 # actual IP or DNS used to connect over the internet
```

This needs to match the address for the TLS/SSL certificate and should not be `localhost` unless TLS is disabled, likewise 0.0.0.0 will not work since this is only used for listening, not connecting to.

### Java SDK

The Java SDK sets the needed variables in akka-ssl.conf. Out of the box this file is not included in the build of the Java SDK. To enable SSL un-comment `include akka-ssl.conf` in the SDK code. You will find it at `harness/java-sdk/src/main/resources/application.conf`, which is set to say 

```
# include "akka-ssl.conf"
include "akka.conf"
```

by default. Un-comment the include and read `akka-ssl.conf` for how to point to the correct keystore similar to the way the Harness server is setup. 

## Harness Common Engine Instance Parameters

Harness provides default behavior for all engines. This can vary by the engine instance, there is one engine instances per `engineId`. In REST terms the `engineId` is the **R**esource id used in the REST API, it is also defined as a required parameter in every engine instance's JSON file.

The common settings for all engine instances available or required in any JSON file are:

```
"engineId": "test_scaffold_resource",
"engineFactory": "com.actionml.templates.scaffold.ScaffoldEngine",
"mirrorType": "localfs",
"mirrorContainer": "!!!< directory for local fs storage of mirrored events >!!!",
"modelContainer": "!!!< directory for local fs storage of models >!!!",
"algorithm": {
  ...
}
```

 - **engineId**: This is a URL encoded REST resource id for the engine instance. It is therefore the "address" by which it is know to the REST API. No default: required.
 - **engineFactory**: The fully qualified classname which contains a factory or construction method named `initAndGet` which takes this JSON file as the input parameter and responds with an HTTP status code and explanation body. No default: required.
 - **mirrorType**: localfs is the only allowed type at present but in teh future the HDFS distributed file system and possible Kafka topic names will be supported. No default: not required. The presence of this and the `mirrorLocation` turns on mirroring.
 - **mirrorContainer**: A descriptor for the location of mirroring for this engine instance. No default: not required. The presence of this and the `mirrorType` turns on mirroring.
 - **modelContainer**: A descriptor for the location the engine instance may use to keep the persistent form of any model needed by the instance. This may be a localfs location or some id required by the Engine. Default to the `HARMESS_HOME` but is by no means required unless the Engine needs it.
 - **algorithm**: this is special to all Engine types and is not specified here.

