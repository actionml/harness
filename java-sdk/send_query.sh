#!/usr/bin/env bash

mvn compile
mvn exec:java -Dexec.mainClass="QueryClientExample"