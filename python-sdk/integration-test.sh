#!/usr/bin/env bash

# This script preforms all functions to create an engine, send it events, and make queries then cleanup
# Todo: check results of queries if possible
# Note: only works with the Contextual Bandit Engine
# Prerequisites: Python 3, harness installed, Contextual Bandit Engine available

set -e
harness stop || true
sleep 2
harness start
sleep 5
harness delete integration_test_1 || true
sleep 2
harness delete integration_test_2 || true
sleep 2
harness add examples/integration_test_1.json
sleep 2
harness add examples/integration_test_2.json
sleep 2
python examples/cb_integration_test_2.py --engine_id integration_test_1 --engine_id_2 integration_test_2 --events_file examples/cb_events.csv --events_file_2 examples/cb_events_2.csv --queries_file examples/cb_queries.csv > test.out
echo "Comparing epected.out to test.out, no differences means a passing test"
diff expected.out new.out
