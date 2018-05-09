#!/usr/bin/env bash

echo
echo "Usage: contextual-bandit-integration-test.sh"
echo "run from harness/java-sdk or from the integration-test.sh"
echo "Make sure to set export HARNESS_CA_CERT=/path/to/harness.pem!!! or all sending to Harness will fail."
echo

# several flags are passed in via export from the integration test, otherwise they are undefined
# so this script will execute the defaults

# point to the harness host, use https://... for SSL and set the credentials if using Auth
# export "HARNESS_CLIENT_USER_ID"=xyz
# export "HARNESS_CLIENT_USER_SECRET"=abc
host=localhost
engine=test_nh
test_queries=data/nh-queries-2-conversion-ids.json
events_2_conversions=data/nh-2-conversion-targets.json
delete_conv_id_event=data/nh-delete-conversion-id.json
sleep_seconds=2

cd example

# initialize these in case not running from integrated test script
skip_restarts=${skip_restarts:-false}
clean_test_artifacts=${clean_test_artifacts:-false}




echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "NAVIGATION HINTING ENGINE"
ECHO "TESTING ONE, AND THEN 2 USERS JOURNEYS AND CONVERSIONS WITH QUERIES"
echo "----------------------------------------------------------------------------------------------------------------"

harness delete ${engine}
sleep $sleep_seconds
harness add data/${engine}.json
sleep $sleep_seconds


echo
echo "Sending navigation and conversion events for 2 targets"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $events_2_conversions" -Dexec.cleanupDaemonThreads=false


echo
echo "Sending hinting queries, should have hints now for 2 conversion ids"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false > nh-hinting-results-2-models.txt


echo
echo "Deleting most popular conversion id"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $delete_conv_id_event" -Dexec.cleanupDaemonThreads=false

echo
echo "Sending hinting queries, should have hints now for only 1 conversion id"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results-2-models.txt


echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "TESTING NAV-HINTING MODEL PERSISTENCE BY RESTARTING HARNESS AND MAKING QUERIES"
echo "----------------------------------------------------------------------------------------------------------------"
echo

if [ "$skip_restarts" == false ]; then
    harness stop
    sleep 10

    harness start -f
    sleep 10

fi

echo
echo "Sending queries after 1 conversion-id"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results-2-models.txt


echo
echo "Sending navigation and conversion events for 2 targets"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $events_2_conversions" -Dexec.cleanupDaemonThreads=false


echo
echo "Sending hinting queries, should have hints now for 2 conversion ids"
echo "but there are more for id 1 so the results should be reversed from first 2 conversion query"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results-2-models.txt

echo
echo "Sending navigation and conversion events for 2 targets"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $events_2_conversions" -Dexec.cleanupDaemonThreads=false


echo
echo "Sending hinting queries, should have hints now for 2 conversion ids"
echo "now there are enough that id2 should be the result like the first query"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results-2-models.txt


echo "---------------------- profile query differences should only be timing ----------------------------"
diff nh-hinting-results-2-models.txt data/expected-nh-hinting-results-2-models-urls.txt | grep Results
echo

if [ "$clean_test_artifacts" == true ]; then
    harness delete ${engine}
fi

cd ..
