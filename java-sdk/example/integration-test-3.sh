#!/usr/bin/env bash

echo
echo "Usage: ./integration-test-3.sh"
#"Usage: ./send_queries host resource-id queries-file.json"
echo
host=localhost
resource_id=test_resource
test_3_phase_1_data=data/test_3_phase_1.json
test_3_phase_2_data=data/test_3_phase_2.json
test_3_phase_3_data=data/test_3_phase_3.json
test_3_queries=data/test_3_queries.json


mvn compile
mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$host $resource_id $test_3_phase_1_data" -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.args="$host $resource_id $test_3_queries" -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$host $resource_id $test_3_phase_2_data" -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.args="$host $resource_id $test_3_queries" -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass="EventClientExample" -Dexec.args="$host $resource_id $test_3_phase_3_data" -Dexec.cleanupDaemonThreads=false
mvn exec:java -Dexec.mainClass="QueryClientExample" -Dexec.args="$host $resource_id $test_3_queries" -Dexec.cleanupDaemonThreads=false
