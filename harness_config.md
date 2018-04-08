# Harness Config

Harness config come in two parts:

 - **Server config**: Defines server settings, how it connects to other services or to applications, security, and other server specific settings. These settings are collected in `harness-env` in the form of environment variables.
 - **Engine config**: Defines a set of parameters than are available to all engine instances via their JSON config files. These include method and location of event mirroring, model storage, factory object name, and other parameters that apply to a specific engine instance but is available to all engines. 

## Harness Server Config

Harness server settings are in `rest/server/bin/harness-env.sh` as shown below. The default setup is for localhost connections, no Auth or TLS. This is good for running everything on a dev machine for experiments.

```
#!/usr/bin/env bash

# Harness Server config, should work as-id unless you are using SSL
# to listen on the host IP address for external connections
# export REST_SERVER_HOST=0.0.0.0 # to listen for external connections
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
# to change the port used
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}

# To connect to a required MongoDB instance or cluster
export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}

# To configure Harness logging
export HARNESS_LOG_CONFIG="${HARNESS_HOME}/conf/logback.xml"
export HARNESS_LOG_PATH="${HARNESS_HOME}/logs"

# =============================================================
# Read no further if you do not need Authentication
# =============================================================

# Harness Auth
# export HARNESS_AUTH_ENABLED=true
export HARNESS_AUTH_ENABLED=${HARNESS_AUTH_ENABLED:-false}
# When auth is enabled there must be an admin user-id set so create one before turning on Auth
# Both the Harness server and the Python CLI need this env var when using Auth
# export ADMIN_USER_ID=some-user-id
# The Python CLI needs to pass the user-id and user-secret to the Python SDK so when using Auth supply a pointer to
# the user-secret here.
# export ADMIN_USER_SECRET_LOCATION=${ADMIN_USER_SECRET_LOCATION:-"$HOME/.ssh/${ADMIN_USER_ID}.secret"}

# =============================================================
# Read no further if you do not need TLS/SSL
# =============================================================

# Harness TLS/SSL server support. A dummy file needs to be provided even if TLS is not used, one is supplied with Harness
export HARNESS_KEYSTORE_PASSWORD=${HARNESS_KEYSTORE_PASSWORD:-23harness5711!}
export HARNESS_KEYSTORE_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/harness.jks}
# export HARNESS_SSL_ENABLED=true # to enable TLS/SSL
export HARNESS_SSL_ENABLED=${HARNESS_SSL_ENABLED:-false}

# Java and Python client SDKs use the following for TLS/SSL
# export HARNESS_SERVER_CERT_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/harness.pem}

# The Python CLI must connect to the external address of the server to use TLS, supply it here
# export HARNESS_EXTERNAL_ADDRESS=1.2.3.4 # to connect with a certificate we need to use the matching address here
export HARNESS_EXTERNAL_ADDRESS=localhost # for non-TLS local connections
```

## Advanced Settings

See [Advanced Settings](advanced_settings.md) for allowing external connections, using Auth, and TLS/SSL.

## Harness Common Engine Instance Parameters

Harness provides default behavior for all engines. This can vary by the engine instance, there is one engine instances per `engineId`. In REST terms the `engineId` is the **R**esource id used in the REST API, it is also defined as a required parameter in every engine instance's JSON file.

The common settings for all engine instances available or required in any JSON file are:

```
"engineId": "test_scaffold_resource",
"engineFactory": "com.actionml.templates.scaffold.ScaffoldEngine",
"comment": "only localfs for Harness < 0.2.0, then hdfs is allowed",
"mirrorType": "localfs",
"comment": "a directory or container for the mirrorType, not a file name",
"mirrorContainer": "!!!< directory for local fs storage of mirrored events >!!!",
"comment": "a directory or container for the mirrorType, not a file name",
"modelContainer": "!!!< directory for local fs storage of models >!!!",
"comment": "it is usually safe to use lower case with snake case for readability",
"sharedDBName": "!!!<name of db to share data>!!!",
"algorithm": {
  ...
}
```

 - **engineId**: This is a URL encoded REST resource id for the engine instance. It is therefore the "address" by which it is know to the REST API. No default: required.
 - **engineFactory**: The fully qualified classname which contains a factory or construction method named `initAndGet` which takes this JSON file as the input parameter and responds with an HTTP status code and explanation body. No default: required.
 - **mirrorType**: `localfs` is the only allowed type at present but in the future the `hdfs` distributed file system and possible Kafka topic names will be supported. No default: not required. The presence of this and the `mirrorContainer` turns on mirroring.
 - **mirrorContainer**: A descriptor for the location of mirroring for this engine instance. This should be a directory or other container, not a file name. If using localfs a subdirectory will be created with the engine-id as a name and files will accumulate labeled for each day of mirroring. No default: not required. The presence of this and the `mirrorType` turns on mirroring.
 - **modelContainer**: A descriptor for the location the engine instance may use to keep the persistent form of any model needed by the instance. This may be a localfs directory or some id required by the Engine. Typically this is a directory, which will have a model named for each engine-id. Defaults to the `HARMESS_HOME` if the Engine needs it.
 - **sharedDBName**: The name of a db shared between all engine instances that as configured to use it. Each Engine decides what it can share. Data stored here must be carefully coordinated between engines, use at your own risk. The primary use would be for user information shared between all Engine instances assigned to a single client app or site. See each Engine docs for specifics.   
 - **algorithm**: this is special to all Engine types and is not specified here.
