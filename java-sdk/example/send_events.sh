#!/usr/bin/env bash

if [ -z "$1" ]
  then
    echo "No argument supplied (event json file)"
else
  mvn compile
  mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$1" -Dexec.cleanupDaemonThreads=false
fi

