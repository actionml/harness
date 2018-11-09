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
"""

CSV_DELIM = ","
PROPERTIES_DELIMITER = "|"
SEED = 0xdeadbeef

def send_event(client):
    """
    Example event JSON
    {
        "event" : "event-name",
        "entityType" : "user",
        "entityId" : "user-id",
        "targetEntityType": "item" # always "item" in PIO data but ignored
        "targetEntityId" : "item-id",
        "properties" : { # map of String: Any
            "conversion": true | false
        },
        "eventTime": "ISO-8601 encoded string", # may be used
        "creationTime": "ISO-8601 encoded string" # ignored
    }
    
    $delete events ignored for PoC
    No $set events supported for PoC
    
    """

    current_date = datetime.datetime.now(pytz.utc)  # - datetime.timedelta(days=2.7)

    client.create(
        event_id="1",  # ignored in input, but may be in imported data from PIO
        event="$delete",
        entity_type="user",
        entity_id="joe",
        # target_entity_type="item",
        # target_entity_id=data[2],
        event_time=current_date,
        # properties={"converted": converted}
    )
    # print("Event: " + str(data))
    client.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="Import sample data for recommendation engine")
    parser.add_argument('--engine_id', default='test_ur_nav_hinting')
    parser.add_argument('--url', default="http://localhost:9090")
    parser.add_argument('--secret', default=None)
    parser.add_argument('--secret_2', default=None)

    args = parser.parse_args()
    print(args)

    events_client = harness.EventsClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500 # ,
        # user_id=args.user_id,
        # user_secret=args.secret
        )
    print(events_client.host)

    send_event(events_client)
