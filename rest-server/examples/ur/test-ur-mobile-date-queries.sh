#!/usr/bin/env bash

curl -H "Content-Type: application/json" -d '
{
  "num": 20
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u-4"
}' http://localhost:9090/engines/test_ur/queries
echo

#============ dateRange filter ============"
if [[ "$OSTYPE" == "linux-gnu" ]]; then
  BEFORE=`date --date="today + 12 hours" --iso-8601=seconds`
  AFTER=`date --date="6 hours ago" --iso-8601=seconds`
else
  # changed as per PR https://github.com/actionml/universal-recommender/pull/49
  # BEFORE=`date -v +1d +"%Y-%m-%dT%H:%M:%SZ"`
  # AFTER=`date -v -1d +"%Y-%m-%dT%H:%M:%SZ"`
  #BEFORE=`date -v +1d -u +%FT%TZ`
  BEFORE=`date -v +12H +%FT%TZ`
  AFTER=`date -v -6H +%FT%TZ`
fi
#echo "before: $BEFORE after: $AFTER"

curl -H "Content-Type: application/json" -d "
{
    \"user\": \"u-4\",
    \"dateRange\": {
        \"name\": \"date\",
        \"before\": \"$BEFORE\",
        \"after\": \"$AFTER\"
    }
}" http://localhost:9090/engines/test_ur/queries
echo ""

curl -H "Content-Type: application/json" -d '
{
  "user": "u-4",
  "rules": [
    {
       "name": "categories",
       "values": ["Google"],
       "bias": -1
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo

curl -H "Content-Type: application/json" -d '
{
  "user": "u-4",
  "rules": [
    {
       "name": "categories",
       "values": ["Google"],
       "bias": 0
    }
  ]
}' http://localhost:9090/engines/test_ur/queries
echo
