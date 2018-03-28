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
engine=test_nh
test_queries=data/nh-queries-urls.json
user1_events=data/nh-pferrel-events-urls.json
user2_events=data/nh-joe-events-urls.json
user1_conversion=data/nh-pferrel-conversion-urls.json
user2_conversion=data/nh-joe-conversion-urls.json
sleep_seconds=2

# export HARNESS_SERVER_CERT_PATH=/Users/pat/harness/rest-server/server/src/main/resources/keys/harness.pem
cd example
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "NAVIGATION HINTING ENGINE"
ECHO "TESTING ONE, AND THEN 2 USERS JOURNEYS AND CONVERSIONS WITH QUERIES"
echo "----------------------------------------------------------------------------------------------------------------"

harness delete ${engine}
sleep $sleep_seconds
harness add data/${engine}.json
sleep $sleep_seconds

h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending navigation events for user pferrel"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user1_events" -Dexec.cleanupDaemonThreads=false
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending hinting queries, no conversions yet so no hints"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false > nh-hinting-results.txt
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending conversion for user pferrel"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user1_conversion" -Dexec.cleanupDaemonThreads=false
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending hinting queries, should have hints now."
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending navigation events for user joe"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user2_events" -Dexec.cleanupDaemonThreads=false
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo
echo "Sending conversion for user joe"
echo
mvn exec:java -Dexec.mainClass="EventsClientExample" -Dexec.args="$host $engine $user2_conversion" -Dexec.cleanupDaemonThreads=false
h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
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

if [ $skip_restarts = false ]; then
    harness stop
    sleep 10
    h=`jps | grep Main | wc -l`
    if [[ "$h" -gt "0" ]]; then
        echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
        exit 1
    fi
    harness start -f
    sleep 10
    h=`jps | grep Main | wc -l`
    if [[ "$h" -gt "1" ]]; then
        echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
        exit 1
    fi
fi

echo
echo "Sending queries after 2 conversion"
echo
mvn exec:java -Dexec.mainClass="QueriesClientExample" -Dexec.args="$host $engine $test_queries" -Dexec.cleanupDaemonThreads=false >> nh-hinting-results.txt

h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
echo "---------------------- profile query differences should only be timing ----------------------------"
diff nh-hinting-results.txt data/expected-nh-hinting-results-urls.txt
echo

if [ $clean_test_artifacts = true ]; then
    harness delete ${engine}
fi

h=`jps | grep Main | wc -l`
if [[ "$h" -gt "1" ]]; then
    echo "==============> Yak $h instances of harness, something failed to stop harness <=============="
    exit 1
fi
cd ..
