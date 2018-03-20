#!/usr/bin/env bash

echo
echo "Usage: ./send_queries host resource-id queries-file.json"
echo
if [ -z "$3" ]
  then
    echo "No queries file specified"
elif [ -z "$2" ]
  then
    echo "No resource-id"
elif [ -z "$1" ]
  then
    echo "No host name"
else
  mvn clean compile
  mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$1 $2 $3" -Dexec.cleanupDaemonThreads=false
fi


