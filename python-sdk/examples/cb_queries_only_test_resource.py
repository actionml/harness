"""
Import sample data for recommendation engine
"""

import harness
import argparse
import random
import datetime
import pytz

"""
example for importing events through the POST /engines/resource-id/events endpoint
taken from the PIO UR import_handmade.py, which imports from a csv
Todo: may want to use JSON for the CB
"""
CSV_DELIM = ","
PROPERTIES_DELIMITER = ":"
SEED = 0xdeadbeef


def execute_queries(client, file):
    f = open(file, 'r')  # json or csv? setup for csv for now

    random.seed(SEED)
    count = 0
    current_date = datetime.datetime.now(pytz.utc)
    print("Making queries...")

    for line in f:
        data = line.rstrip('\r\n').split(CSV_DELIM)
        # queries are:
        # user-id,group-id

        response = client.send_query({"user": data[0], "groupId": data[1]})

        print("Result: \"\nuser\": " + data[0] + response.json_body)
        count += 1

if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="Import sample data for recommendation engine")
    parser.add_argument('--engine_id', default='test_resource')
    parser.add_argument('--url', default="http://localhost:9090")
    parser.add_argument('--events_file', default="./cb_events.csv")
    parser.add_argument('--queries_file', default="./cb_queries.csv")

    args = parser.parse_args()
    print(args)

    print("Queries for " + args.engine_id)
    query_client = harness.QueriesClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500)

    execute_queries(query_client, args.queries_file)
    query_client.close()

