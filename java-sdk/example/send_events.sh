#!/usr/bin/env bash

echo
echo "Usage: ./send_events host resource-id events-file.json"
echo
if [ -z "$3" ]
  then
    echo "No events file specified"
elif [ -z "$2" ]
  then
    echo "No resource-id"
elif [ -z $1 ]
  then
    echo "No host name"
else
  mvn compile
  mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$1 $2 $3" -Dexec.cleanupDaemonThreads=false
fi

