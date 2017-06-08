#!/usr/bin/env bash

if [ -z "$1" ]
  then
    echo "No argument supplied for queries json file"
else
  mvn clean compile
  mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.args="$1" -Dexec.cleanupDaemonThreads=false
fi


