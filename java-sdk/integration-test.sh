#!/usr/bin/env bash

echo
echo "Usage: integration-test.sh <-n> <-c> <-s> <-p> <-m>"
echo "    -n: run only the nav-hinting-integration-test.sh, default runs all tests."
echo "    -c: run only the contextual-bandit-integration-test.sh, default runs all tests."
echo "    -s: skip restarts. Default do harness restarts to test persistence. Skipping will allow"
echo "        the same server to run throughout the test, as when using a debugger launched Harness."
echo "        NOTE: this flag assumes Harness is already running."
echo "    -p: preserve data in the DB and engine instance, default clean Harness of test artifacts."
echo "    -m: clean Mongo of all TEST dbs before starting Harness, default no Mongo DBS are dropped."
echo "        WARNING: Be VERY CAREFUL with this parameter because it may wipe the harness_meta_store,"
echo "        which contains all engine instance data. A backup of the DB is made and can be restored "
echo "        using the MongoDB shell but this option is best used with great care."
echo "        -m is good for CI type automated tests. DBs are only dropped with Harness shut down so"
echo "        this option is ignored if -s is skipping restarts."
echo

clean_mongo=false
do_nh_test=true
do_cb_test=true
export skip_restarts=false # send to child scripts
export clean_test_artifacts=true

set -e # exit on any error

m=`jps -lm | grep Main | wc -l`

if (( m > 1 )); then
    echo
    echo "====== More than one instance of Harness running, this is likely an error ======"
    echo
    exit 1
fi

while [ -n "$1" ]; do
    case "$1" in
        -p)
            export clean_test_artifacts=false
            ;;
        -m)
            clean_mongo=true
            ;;
        -n)
            do_nh_test=true
            do_cb_test=false
            ;;
        -c)
            do_cb_test=true
            do_nh_test=false
            ;;
        -s)
            if (( m < 1 )); then
                echo
                echo "Harness must be running to use -s."
                echo
                exit 1
            fi
            export skip_restarts=false # send to child scripts
            ;;
        *)  echo "Bad param, see usage."
            exit 1
            ;;
    esac
    shift
done


if [ "$skip_restarts" = false ]; then
    harness stop -f
    sleep 5
    if [ "$clean_mongo" = true ]; then
        echo "Wiping the database"
        mongo clean_harness_mongo.js # drops meta store (all engines) and specific dbs used by test engine instances
    fi
    # Todo: clean the db of the harness_meta_store and any of the dbs used by test engine instances
    # this makes the test immune to schema changes
    harness start -f
    sleep 10
fi

if [ "$do_cb_test" = true ]; then
    ./contextual-bandit-integration-test.sh
fi

if [ "$do_nh_test" = true ]; then
    ./nav-hinting-integration-test.sh
fi


echo
echo "========================================================================================================="
echo " Final Test Results, none is a passing test."
echo

set +e # exit codes below should be ignored

if [ "$do_cb_test" = true ]; then
    echo "---------------------- Important differences: Contextual Bandit profile data ----------------------------"
    diff example/test-profile-results.txt example/data/expected-test-profile-results.txt | grep Results
    echo
    echo "---------------------- Important differences: Contextual Bandit behavior data ----------------------------"
    diff example/test-behavior-results.txt example/data/expected-test-behavior-results.txt | grep Results
    echo
fi

if [ "$do_nh_test" = true ]; then
    echo "---------------------- Important differences: Navigation Hinting queries ----------------------------"
    diff example/nh-hinting-results.txt example/data/expected-nh-hinting-results-urls.txt | grep Results
    echo
fi
