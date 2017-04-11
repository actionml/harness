#!/usr/bin/env bash

mvn clean compile
mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.cleanupDaemonThreads=false