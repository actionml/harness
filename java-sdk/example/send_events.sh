#!/usr/bin/env bash

echo "Usage: ./send_events resource-id events-file.json"
if [ ! -f "$2" ]
  then
    echo "No events file specified"
elif [ -z $1 ]
  then
    echo "No resource-id"
else

  mvn compile
  mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$1 $2" -Dexec.cleanupDaemonThreads=false
fi

