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
# for real CLI test: engine=test_ur_nav_hinting
engine=test_ur_nav_hinting
test_queries=data/nh-queries-urls.json
user_events=data/ur_nav_hinting_handmade_data.csv

training_sleep_seconds=30

# initialize these in case not running from integrated test script
skip_restarts=${skip_restarts:-false}
clean_test_artifacts=${clean_test_artifacts:-false}
test_results=data/expected-urnh-results.txt


if [ "$skip_restarts" = false ]; then
    harness stop
    harness start -f
    sleep 10
fi

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "PERSONALIZED NAVIGATION HINTING ENGINE"
echo "----------------------------------------------------------------------------------------------------------------"

echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add data/${engine}.json
#sleep $sleep_seconds

harness status

echo
echo "Sending all personalization events"
echo
python3 ur_nav_hinting_import_handmade.py

echo
echo "Training a new model--THIS WILL TAKE SOME TIME (30 SECONDS?)"
echo
harness train $engine
sleep $training_sleep_seconds # wait for training to complete

echo
echo "Sending hinting queries, joe and john should get the same results since they have identical behavior"
echo
python3 test_urnh_queries.py > test-urnh.out


echo "---------------------- There should be no important differences ----------------------------"
diff test-urnh.out ${test_results} | grep result
echo

