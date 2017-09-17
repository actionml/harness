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
  echo "host: ${1} resource: ${2} file: ${3}"
  mvn compile
  mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$1 $2 $3" -Dexec.cleanupDaemonThreads=false
fi

