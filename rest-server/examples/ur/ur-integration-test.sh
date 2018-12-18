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
engine=test_ur
engine_json=examples/ur/test_ur_mobile_device.json
test_queries=examples/ur/test-ur-mobile-device-queries.sh
user_events=examples/ur/sample-mobile-device-ur-data.csv
actual_query_results=actual_ur_results.out

training_sleep_seconds=30

# initialize these in case not running from integrated test script
skip_restarts=${skip_restarts:-true}
clean_test_artifacts=${clean_test_artifacts:-false}
expected_test_results=examples/ur/expected-ur-results.txt


if [ "$skip_restarts" = false ]; then
    harness stop
    harness start -f
    sleep 10
fi

echo
echo "----------------------------------------------------------------------------------------------------------------"
echo "Universal Recommender Integration tests"
echo "----------------------------------------------------------------------------------------------------------------"

echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add ${engine_json}
#sleep $sleep_seconds

echo
echo "Sending all personalization events"
echo
python3 examples/ur/import_mobile_device_ur_data.py --input_file ${user_events}

echo
echo "Training a new model--THIS WILL TAKE SOME TIME (30 SECONDS?)"
echo
harness train $engine
sleep $training_sleep_seconds # wait for training to complete

echo
echo "Sending hinting queries"
echo
./${test_queries} > ${actual_query_results}

echo
echo "---------------------- Testing Event Aliases                    ----------------------------"

engine_aliases_json=examples/ur/test_ur_event_aliases.json
user_events_aliases=examples/ur/sample-event-alias-ur-data.csv
actual_query_results_aliases=actual_ur_aliases_results.out

echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add ${engine_aliases_json}
#sleep $sleep_seconds

echo
echo "Sending all personalization events"
echo
python3 examples/ur/import_mobile_device_ur_data.py --input_file ${user_events_aliases}

echo
echo "Training a new model--THIS WILL TAKE SOME TIME (30 SECONDS?)"
echo
harness train $engine
sleep $training_sleep_seconds # wait for training to complete

echo
echo "Sending hinting queries"
echo
./${test_queries} > ${actual_query_results_aliases}


echo "---------------------- There should be no important differences ----------------------------"
diff ${actual_query_results} ${expected_test_results} | grep result
diff ${actual_query_results_aliases} ${expected_test_results} | grep result
echo

