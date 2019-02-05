#!/usr/bin/env bash
set -e
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
diffs_and_errors_file=diffs_and_errors.txt
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
echo
: <<'END' # block comment beginning look for END

echo "---------------------- Testing Simple Personalized Recs with Business Rules         ----------------------------"


echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add ${engine_json} || true
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
echo "Sending UR queries"
echo
./${test_queries} > ${actual_query_results}
END
#: <<'END' # block comment beginning look for END

echo
echo "---------------------- Testing Event Aliases -------------------------------------------------------------------"

engine_aliases_json=examples/ur/test_ur_event_aliases.json
user_events_aliases=examples/ur/sample-event-alias-ur-data.csv
actual_query_results_aliases=actual_ur_aliases_results.out

echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add ${engine_aliases_json} || true
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
echo "Sending UR queries"
echo
./${test_queries} > ${actual_query_results_aliases}

#END

echo "---------------------- Testing Queries Filtered by Dates -------------------------------------------------------"

: <<'END' # block comment beginning look for END

engine_dates_json=examples/ur/test_ur_mobile_device_dates.json
user_events_dates=examples/ur/sample-mobile-device-ur-data.csv
actual_query_results_dates=actual_ur_dates_results.out
expected_test_results_dates=examples/ur/expected-ur-date-results.txt
test_date_queries=examples/ur/test-ur-mobile-date-queries.sh


echo "Wipe the Engine clean of data and model first"
harness delete ${engine}
#sleep $sleep_seconds
harness add ${engine_dates_json} || true
#sleep $sleep_seconds

echo
echo "Sending all personalization events with dates"
echo
python3 examples/ur/import_mobile_device_ur_data.py --input_file ${user_events_dates} --with_dates true

echo
echo "Training a new model--THIS WILL TAKE SOME TIME (30 SECONDS?)"
echo
harness train $engine
sleep $training_sleep_seconds # wait for training to complete

echo
echo "Sending UR queries"
echo

./${test_date_queries} > ${actual_query_results_dates}
END

# echo "---------------------- Below there should be no differences reported -------------------------------------------"
rm -f ${diffs_and_errors_file}

#diff ${actual_query_results} ${expected_test_results} | grep "result" >> ${diffs_and_errors_file}
#cat ${actual_query_results} | grep "error" >> ${diffs_and_errors_file}
diff ${actual_query_results_aliases} ${expected_test_results} | grep "result" >> ${diffs_and_errors_file}
cat ${actual_query_results_aliases} | grep "error"  >> ${diffs_and_errors_file}
#diff ${actual_query_results_dates} ${expected_test_results_dates} | grep "result"  >> ${diffs_and_errors_file}
#cat ${actual_query_results_dates} | grep "error" >> ${diffs_and_errors_file}

if [ -s ${diffs_and_errors_file} ]
then
   echo " Some differences between actual and expected or server errors, check the actual results files. "
   cat ${diffs_and_errors_file}
   exit 1
else
   echo "Tests pass."
   exit 0
fi
