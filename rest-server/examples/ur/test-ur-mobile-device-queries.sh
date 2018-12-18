#!/usr/bin/env bash

curl -H "Content-Type: application/json" -d '
{
  "num": 20
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u1"
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2"
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u-3"
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u-4"
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u5"
}' http://localhost:9090/engines/test_ur/queries
echo

echo
echo "============= Business Rules ============="
echo "============= Inclusion      ============="
echo

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
       "values": ["Tablets"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Phones"],
       "bias": -1
    }
  ]
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

echo
echo "============= Exclusion      ============="
echo

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
       "values": ["Tablets"],
       "bias": 0
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Phones"],
       "bias": 0
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Tablets", "Phones"],
       "bias": 0
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

echo
echo "============= Boost          ============="
echo

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
       "values": ["Tablets"],
       "bias": 20
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Phones"],
       "bias": 20
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Tablets", "Phones"],
       "bias": 20
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

echo
echo "============= Include A & B ============="
echo

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
       "values": ["Tablets"],
       "bias": -1
    },{
       "name": "categories",
       "values": ["Apple"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Tablets"],
       "bias": -1
    },{
       "name": "categories",
       "values": ["Microsoft"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "U 2",
  "rules": [
    {
       "name": "categories",
       "values": ["Tablets"],
       "bias": -1
    },{
       "name": "categories",
       "values": ["Google"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

