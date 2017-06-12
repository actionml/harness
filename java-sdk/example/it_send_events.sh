#!/usr/bin/env bash

echo "Usage: ./send_events resource-id events-file.json"
if [ ! -f "$2" ]; then
    echo "No events file specified"
elif [ -z $1 ]; then
    echo "No resource-id"
else
    REST_SERVER_HOST=${REST_SERVER_HOST:-"0.0.0.0"}
    REST_SERVER_PORT=${REST_SERVER_PORT:-9090}
    echo "Run with params: resourceId: $1, eventsFile: $2, host: ${REST_SERVER_HOST}, port: ${REST_SERVER_PORT}"
    mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$1 $2 ${REST_SERVER_HOST} ${REST_SERVER_PORT}" -Dexec.cleanupDaemonThreads=false
fi

