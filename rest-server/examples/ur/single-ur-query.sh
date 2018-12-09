#!/usr/bin/env bash

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2"
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Tablets", "Phones"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

