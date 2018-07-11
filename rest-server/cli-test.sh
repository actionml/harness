#!/usr/bin/env bash

harness stop
harness start -f
harness add data/scaffold.json
harness status engines
harness add data/test_nh.json
harness status engines
harness add data/test_cb.json
harness status engines
harness delete scaffold
harness delete test_cb
harness delete test_nh
harness stop
