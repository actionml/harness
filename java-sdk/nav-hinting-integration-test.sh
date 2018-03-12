#!/usr/bin/env bash

echo
echo "Usage: contextual-bandit-integration-test.sh run from harness/java-sdk without SSL or Auth"
echo
# point to the harness host, use https://... for SSL and set the credentials if using Auth
# export "HARNESS_CLIENT_USER_ID"=xyz
# export "HARNESS_CLIENT_USER_SECRET"=abc
host=localhost
engine=hinting
test_queries=data/nh-queries-urls.json
user1_events=data/nh-pferrel-events-urls.json
user2_events=data/nh-joe-events-urls.json
user1_conversion=data/nh-pferrel-conversion-urls.json
user2_conversion=data/nh-joe-conversion-urls.json
sleep_seconds=1

# export HARNESS_SERVER_CERT_PATH=/Users/pat/harness/rest-server/server/src/main/resources/keys/harness.pem
cd example

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "NAVIGATION HINTING ENGINE"
ECHO "TESTING ONE, AND THEN 2 USERS JOURNEYS AND CONVERSIONS WITH QUERIES"
echo "----------------------------------------------------------------------------------------------------------------"
harness delete hinting
sleep $sleep_seconds
harness add ../../rest-server/data/hinting.json
sleep $sleep_seconds

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
#harness stop
#sleep $sleep_seconds
#harness start -f
#3sleep 10

echo
echo "Sending queries after 2 conversion"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt

echo "---------------------- profile query differences should only be timing ----------------------------"
diff nh-hinting-results.txt data/expected-nh-hinting-results-urls.txt
echo

cd ..
