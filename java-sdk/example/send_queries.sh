#!/usr/bin/env bash

echo "Usage: ./send_queries resource-id queries-file.json"
if [ -z "$2" ]
  then
    echo "No queries file specified"
elif [ -z $1 ]
  then
    echo "No resource-id"
else
  mvn clean compile
  mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.args="$1 $2" -Dexec.cleanupDaemonThreads=false
fi


