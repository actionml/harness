#!/usr/bin/env bash

echo
echo "Usage: contextual-bandit-integration-test.sh"
echo "run from harness/java-sdk or from the integration-test.sh"
echo "export HARNESS_CLIENT_USER_ID and HARNESS_CLIENT_USER_SECRET before running against"
echo "Harness with TLS and Auth"
echo

# several flags are passed in via export from the integration test, otherwise they are undefined
# so this script will execute the defaults

# point to the harness host, use https://... for SSL and set the credentials if using Auth
# export "HARNESS_CLIENT_USER_ID"=xyz
# export "HARNESS_CLIENT_USER_SECRET"=abc
host=localhost
engine=test_ur_nav_hinting
test_queries=data/nh-queries-urls.json
user_events=data/ur_nav_hinting_handmade_data.csv

sleep_seconds=2

# initialize these in case not running from integrated test script
skip_restarts=${skip_restarts:-false}
clean_test_artifacts=${clean_test_artifacts:-false}




echo
echo "----------------------------------------------------------------------------------------------------------------"
echo
echo "PERSONALIZED NAVIGATION HINTING ENGINE"
echo "----------------------------------------------------------------------------------------------------------------"

harness delete ${engine}
sleep $sleep_seconds
harness add data/${engine}.json
sleep $sleep_seconds

harness status

echo
echo "Sending navigation events for user pferrel"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user1_events" -Dexec.cleanupDaemonThreads=false


echo
echo "Sending hinting queries, no conversions yet so no hints"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false > nh-hinting-results.txt


echo
echo "Sending conversion for user pferrel"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user1_conversion" -Dexec.cleanupDaemonThreads=false


echo
echo "Sending hinting queries, should have hints now."
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt


echo
echo "Sending navigation events for user joe"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user2_events" -Dexec.cleanupDaemonThreads=false

echo
echo "Sending conversion for user joe"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user2_conversion" -Dexec.cleanupDaemonThreads=false


echo
echo
echo "Sending queries after 2 conversion"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt


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
echo "Sending queries after 2 conversion"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt


echo "---------------------- profile query differences should only be timing ----------------------------"
diff nh-hinting-results.txt data/expected-nh-hinting-results-urls.txt | grep Results
echo

if [ "$clean_test_artifacts" == true ]; then
    harness delete ${engine}
fi

cd ..
