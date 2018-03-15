#!/usr/bin/env bash

echo
echo "Usage: contextual-bandit-integration-test.sh"
echo "run from harness/java-sdk or from the integration-test.sh"
echo

# several flags are passed in via export from the integration test, otherwise they are undefined
# so this script will execute the defaults


# point to the harness host, use https://... for SSL and set the credentials if using Auth
# export "HARNESS_CLIENT_USER_ID"=xyz
# export "HARNESS_CLIENT_USER_SECRET"=abc
host=localhost
engine_1=test_cb
engine_2=test_cb_2
test_queries=data/2-user-query.json
engine_1_behavior_events=data/joe-context-tags-2.json
engine_1_profile_events=data/joe-profile-location.json
engine_2_behavior_events=data/john-context-tags-2.json
engine_2_profile_events=data/john-profile-location.json
sleep_seconds=1

# export HARNESS_CA_CERT=/Users/pat/harness/rest-server/server/src/main/resources/keys/harness.pem
cd example


if [ $skip_restarts = false ]; then
    harness stop -f
    sleep 5
    harness start -f
    sleep 10
fi

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "TESTING SIMILAR PROFILES, 2 PEOPLE $set, INTO 2 DIFFERENT ENGINES"
echo "----------------------------------------------------------------------------------------------------------------"
harness delete test_cb
sleep $sleep_seconds
harness add data/test_cb.json
sleep $sleep_seconds
harness delete test_cb_2
sleep $sleep_seconds
harness add data/test_cb_2.json

echo
echo "Sending events to create testGroup: 1, user: joe, and one conversion event with no contextualTags to test_cb"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine_1 $engine_1_profile_events" -Dexec.cleanupDaemonThreads=false
echo
echo "Sending events to create testGroup: 1, user: john, and one conversion event with no contextualTags to test_cb_2"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine_2 $engine_2_profile_events" -Dexec.cleanupDaemonThreads=false
echo
echo "Sending queries for joe and john to test_cb"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_1 $test_queries" -Dexec.cleanupDaemonThreads=false > test-profile-results.txt
echo
echo "Sending queries for joe and john to test_cb_2"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_2 $test_queries" -Dexec.cleanupDaemonThreads=false >> test-profile-results.txt

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "TESTING SIMILAR BEHAVIORS, 2 PEOPLE'S CONVERSIONS, INTO 2 DIFFERENT ENGINES"
echo "----------------------------------------------------------------------------------------------------------------"
echo
harness delete test_cb
sleep $sleep_seconds
harness add data/test_cb.json
sleep $sleep_seconds
harness delete test_cb_2
sleep $sleep_seconds
harness add data/test_cb_2.json

echo
echo "Sending events to create testGroup: 1, user: joe, and one conversion event with contextualTags to test_cb"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine_1 $engine_1_behavior_events" -Dexec.cleanupDaemonThreads=false
echo
echo "Sending events to create testGroup: 1, user: john, and one conversion event with contextualTags to test_cb_2"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine_2 $engine_2_behavior_events" -Dexec.cleanupDaemonThreads=false
echo
echo "Sending queries for joe and john to test_cb"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_1 $test_queries" -Dexec.cleanupDaemonThreads=false > test-behavior-results.txt
echo
echo "Sending queries for joe and john to test_cb_2"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_2 $test_queries" -Dexec.cleanupDaemonThreads=false >> test-behavior-results.txt


echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "TESTING CONTEXTUAL BANDIT MODEL PERSISTENCE BY RESTARTING HARNESS AND MAKING QUERIES"
echo "----------------------------------------------------------------------------------------------------------------"
echo

if [ $skip_restarts = false ]; then
    harness stop -f
    sleep 5
    harness start -f
    sleep 10
fi

echo
echo "Sending queries for joe and john to test_cb"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_1 $test_queries" -Dexec.cleanupDaemonThreads=false > test-behavior-results.txt
echo
echo "Sending queries for joe and john to test_cb_2"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine_2 $test_queries" -Dexec.cleanupDaemonThreads=false >> test-behavior-results.txt


echo "---------------------- profile query differences should only be timing ----------------------------"
diff test-profile-results.txt data/expected-test-profile-results.txt
echo
echo "---------------------- behavior differences should only be timing ----------------------------"
diff test-behavior-results.txt data/expected-test-behavior-results.txt
echo

if [ $clean_test_artifacts = true ]; then
    harness delete test_cb
    harness delete test_cb_2
fi

cd ..
#echo "Ending directory"
#pwd
