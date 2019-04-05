# Harness Config

The Harness configuration is global to all Engine Instances, Engine Instance config is for one instance and contains parameters important to the Engine's Algorithm.

 - **Server configuration**: Defines server settings, how it connects to other services or to applications, security, and other server specific settings. These settings are collected in `harness-env` in the form of environment variables.
 - **Engine config parameters**: Defines a set of *generic* parameters that are available to all Engine Instances via their JSON config files. These include method and location of event mirroring, model storage, factory object name, and other parameters that apply to a specific engine instance but is available to all engines. 

## Harness Server Config

Harness server settings are in `rest/server/bin/harness-env.sh` as shown below. The default setup is for localhost connections, no Auth or TLS. This is good for running everything on a dev machine for experiments.

```
# harness environment file (sourced by scripts)

# Harness Server config, should work as-id unless you are using SSL
# to listen on the host IP address for external connections
# export REST_SERVER_HOST=0.0.0.0 # to listen for external connections
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
# to change the port used
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}

# MongoDB setup
# todo: allow for multiple servers in cluster
export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}

# HDFS setup
# This should be set by Hadoop, otherwise set it here
#export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-"/etc/hadoop"}
# Files can be addressed as hdfs://<some-name-node>:9000 by default but change the master and port below
# if needed
#export HDFS_MASTER=${HDFS_MASTER:-localhost}
#export HDFS_MASTER_PORT=${HDFS_MASTER_PORT:-9000}

# Elasticsearch setup (optional: used by UR and similar Engines)
# todo: need to allow for multiple servers for cluster operation of ES
export ELASTICSEARCH_REST_HOST=${ELASTICSEARCH_REST_HOST:-localhost}
export ELASTICSEARCH_REST_PORT=${ELASTICSEARCH_REST_PORT:-9200}
export ELASTICSEARCH_REST_PROTOCOL=${ELASTICSEARCH_REST_PROTOCOL:-http}

# To configure Harness logging
export HARNESS_LOG_CONFIG="${HARNESS_HOME}/conf/logback.xml"
export HARNESS_LOG_PATH="${HARNESS_LOG_PATH:-$HARNESS_HOME/logs}"

# =============================================================
# Only change to enable TLS/SSL
# =============================================================

# export HARNESS_SSL_ENABLED=true # to enable TLS/SSL using the rest below for "localhost" keys passwords and certs
#export HARNESS_SSL_ENABLED=${HARNESS_SSL_ENABLED:-false}
export HARNESS_SSL_ENABLED=true

# Harness TLS/SSL server support. A file needs to be provided even if TLS is not used, one is supplied for "localhost"
export HARNESS_KEYSTORE_PASSWORD=${HARNESS_KEYSTORE_PASSWORD:-changeit}
export HARNESS_KEYSTORE_PATH=${HARNESS_KEYSTORE_PATH:-$HARNESS_HOME/conf/harness.jks}

# Java and Python client SDKs use the following for TLS/SSL, not used by the server
# the file provided works with localhost, create your own for some other IP address
export HARNESS_SERVER_CERT_PATH=${HARNESS_SERVER_CERT_PATH:-$HARNESS_HOME/conf/harness.pem}

# The Python CLI must connect to the external address of the server to use TLS, supply it here
# export HARNESS_EXTERNAL_ADDRESS=0.0.0.0 # address to listen on, 0.0.0.0 is typical for external connections
export HARNESS_EXTERNAL_ADDRESS=localhost

# =============================================================
# Only used for Authentication
# =============================================================

# Harness Auth-Server setup
# export HARNESS_AUTH_ENABLED=true
export HARNESS_AUTH_ENABLED=${HARNESS_AUTH_ENABLED:-false}
# When auth is enabled there must be an admin user-id set so create one before turning on Auth
# Both the Harness server and the Python CLI need this env var when using Auth
# export ADMIN_USER_ID=some-user-id
# The Python CLI needs to pass the user-id and user-secret to the Python SDK so when using Auth supply a pointer to
# the user-secret here.
# export ADMIN_USER_SECRET_LOCATION=${ADMIN_USER_SECRET_LOCATION:-"$HOME/.ssh/${ADMIN_USER_ID}.secret"}
```

## Advanced Settings

See [Advanced Settings](advanced_settings.md) for allowing external connections, using Auth, and TLS/SSL.

## Harness Common Engine Instance Parameters

Harness provides default behavior for all engines. Common settings for all engine instances available or required in any JSON file are:

```
"engineId": "test_scaffold_resource",
"engineFactory": "com.actionml.engines.scaffold.ScaffoldEngine",
"mirrorType": "localfs" | "hdfs",
"mirrorContainer": "< directory for storage of mirrored events >",
"modelContainer": "< directory for storage of models >",
"sharedDBName": "<name of db to share data>",
"algorithm": {
  ...
}
```

 - **engineId**: This is a URL encoded REST resource id for the engine instance. It is therefore the "address" by which it is know to the REST API. No default: required.

 - **engineFactory**: The fully qualified classname which contains a factory method named `apply` which takes this JSON file as the input parameter and responds with an HTTP status code and explanation body. No default, required.

 - **mirrorType**: `localfs` or `hdfs` are supported as of Harness 0.3.0. In the future other possible stores include Kafka topics may be supported. Default: no mirroring, not required. The presence of this and the `mirrorContainer` turns on mirroring. **Beware**: The `mirrorType` also defines the type of storage for the `harness import ...` command so the imported files must be in `localfs` or `hdfs` as specified here. This is to make re-importing of mirrored events easier but you cannot import from the `mirrorContainer` directly since this could cause an infinite loop. See the `harness import ...` command under [Commands](commands.md) Optional, no default.
 
 - **mirrorContainer**: A descriptor for the location of mirroring for this engine instance. This should be a directory or other container, not a file name. If using localfs or HDFS a subdirectory will be created with the engine-id as a name and files will accumulate labeled for each day of mirroring. No default: not required. The presence of this and the `mirrorType` turns on mirroring.  Optional, no default.

    Local filesystem example:
    
    ```
    "mirrorType": "localfs",
    "mirrorContainer": "/home/aml/mirrors",
    ```
    
    HDFS example:
    
    ```
    "mirrorType": "hdfs",
    "mirrorContainer": "hdfs://your-hdfs-server:9000/mirrors"
    ```
    
    These containers must exist before mirroring will be done. The port should correspond to the one used by HDFS, which by default is usually `9000`.
    
 - **modelContainer**: A descriptor for the location the engine instance may use to keep the persistent form of any model needed by the instance. This may be a localfs directory or some id required by the Engine. Typically this is a directory, which will have a model named for each engine-id. Optional, defaults to the root of Harness.

 - **sharedDBName**: **EXPERIMENTAL** optional, this should be a snake-case name associated with the database used to store data shared across Engine Instances. One `sharedDBName` means to share all **usable** data across all Engine Instances that have this setting. **Note**: most Engine types will not be able to share data with another type but Engines of the same type may well be able to share. See Engine specific documentation.

 - **algorithm**: The parameters control the operation of the Algorithm and so are specified in the Engine documents.

 - **some-other-sections**: for instance there may be a section describing Spark params called `sparkConf`. Arbitrary sections can be added to pass Engine specific information. The information should be considered immutable. It takes a `harness update <some-engine-json-file>` to update it. See specific Engine docs for more information about what can be modified.