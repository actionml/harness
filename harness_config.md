# Harness Config

Harness comes with a set of default config details that work for single machine installation. To override these see `rest/server/bin/harness-env.sh` as shown below. 

```
#!/usr/bin/env bash

# Type of mirroring, localfs is the only supported method currently
# export MIRROR_TYPE=localfs
# The root of mirroring, the engine-id will be appended to this and collections of raw events will be timestamped in files
# export MIRROR_CONTAINER_NAME=/path/to/mirror/directory

# export REST_SERVER_HOST=0.0.0.0 # for allowing outside connections
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}

export MONGO_HOST=${MONGO_HOST:-localhost}
export MONGO_PORT=${MONGO_PORT:-27017}

export HARNESS_LOG_CONFIG="${HARNESS_HOME}/conf/logback.xml"
export HARNESS_LOG_PATH="${HARNESS_HOME}/logs"

# Reset
NC='\033[0m'           # Text Reset

# Regular Colors
RED='\033[0;31m'          # Red
GREEN='\033[0;32m'        # Green
YELLOW='\033[0;33m'       # Yellow
BLUE='\033[0;34m'         # Blue
PURPLE='\033[0;35m'       # Purple
CYAN='\033[0;36m'         # Cyan
WHITE='\033[0;37m'        # White

LINE="=================================================================="

GLINE="${GREEN}${LINE}"
RLINE="${LINE}${NC}"

```

## Accepting Outside Connections

The first thing to change is that the server must bind to `0.0.0.0` instead of `localhost` if it is to accept connections from other servers.

```
export REST_SERVER_HOST=0.0.0.0
```

## Mirroring

Mirroring is like an instant backup of all events that have been sent to all Harness engines. They are bundled into timestamped collections located at the `MIRROR_CONTAINER_NAME`. Mirroring is generally done to the localfs or hdfs (not implemented yet) and are files that have one event per line.

To setup Mirroring for the entire server use the following config:

```
# export MIRROR_TYPE=localfs
# export MIRROR_CONTAINER_NAME=/path/to/mirror/directory
```

Basic mirroring can also be imported with `harness import ...` but beware that importing from the location events are mirrored to will cause an error since imported events are also mirrored. MOVE TG