#!/usr/bin/env bash

curl -H "Content-Type: application/json" -d '
{
  "user": "jerry",
  "eligibleNavIds": ["http://nav.domain1", "http://nav.domain2", "http://nav.domain3", "http://nav.domain4", "http://nav.domain5", "http://nav.domain6", "http://nav.domain7"]
}' http://localhost:9090/engines/ur_nav_hinting/queries
