"""
Queries to test the URNavHinting Engine with sample data
"""

import harness
import argparse
import random
import datetime
import pytz

"""
Example sending events through the POST /engines/engine-id/queries endpoint
Queries are defined in a csv
"""

CSV_DELIM = ","
NAV_IDS_DELIMITER = "|"
SEED = 0xdeadbeef

def send_queries(client, file):
    f = open(file, 'r')  # a csv file
    """ 
    Curl version of a valid URNavHintingEngine query
    
    curl -H "Content-Type: application/json" -d '
    {
      "user": "joe",
      "eligibleNavIds": ["http://nav.domain1", "http://nav.domain2", "http://nav.domain3", "http://nav.domain4", "http://nav.domain5", "http://nav.domain6", "http://nav.domain7"]
    }' http://localhost:9090/engines/ur_nav_hinting/queries
   
    The CSV has 2 columns for the user and the eligibleNavIds the latter are in one column but separated by the
    NAV_IDS_DELIMITER
    
    random.seed(SEED)
    count = 0
    now_date = datetime.datetime.now(pytz.utc)  # - datetime.timedelta(days=2.7)
    current_date = now_date
    event_time_increment = datetime.timedelta(days= -0.8)
    available_date_increment = datetime.timedelta(days= 0.8)
    event_date = now_date - datetime.timedelta(days= 2.4)
    available_date = event_date + datetime.timedelta(days=-2)
    expire_date = event_date + datetime.timedelta(days=2)
    """

    print("Making queries...")
    count = 0

    for line in f:
        # print(line)
        data = line.rstrip('\r\n').split(CSV_DELIM)
        user = data[0]
        eligible_nav_ids = data[1].split(NAV_IDS_DELIMITER)

        print("user: " + user + "eligibleNavIds: " + str(eligible_nav_ids))

        # CSV has
        # user,list-of-eligible-nav-ids with the list | separated

        response = client.send_query({"user": user, "eligibleNavIds": eligible_nav_ids})

        print("Result for user: " + data[0] + "\n" + response.json_body.replace("\n", ""))
        print("Result for user: " + data[0] + "\n" + response.json_body) # leaves pretty result for debugging
        count += 1

    print(str(count) + " queries sent")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="Import sample data for recommendation engine")
    parser.add_argument('--engine_id', default='test_ur_nav_hinting')
    parser.add_argument('--url', default="http://localhost:9090")
    parser.add_argument('--input_file', default="./data/ur_nav_hinting_handmade_queries.csv")
    parser.add_argument('--secret', default=None)
    parser.add_argument('--secret_2', default=None)

    args = parser.parse_args()
    print(args)

    queries_client = harness.QueriesClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500 # ,
        # user_id=args.user_id,
        # user_secret=args.secret
    )
    print(queries_client.host)

    send_queries(queries_client, args.input_file)
    queries_client.close()
