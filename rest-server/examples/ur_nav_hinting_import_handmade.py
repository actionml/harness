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

def import_events(client, file):
    f = open(file, 'r')  # a csv file
    """ 
    indicator event
    {
        "event" : "event-name",
        "entityType" : "user",
        "entityId" : "user-id",
        "targetEntityType": "item" # always "item" in PIO data but ignored
        "targetEntityId" : "item-id",
        "properties" : {
            "conversion": true | false
        },
        "eventTime": "ISO-8601 encoded string",
        "creationTime": "ISO-8601 encoded string"
    }
    
    $delete events ignored for PoC
    No $set events supported for PoC
    
    """
    random.seed(SEED)
    count = 0
    now_date = datetime.datetime.now(pytz.utc)  # - datetime.timedelta(days=2.7)
    current_date = now_date
    event_time_increment = datetime.timedelta(days= -0.8)
    available_date_increment = datetime.timedelta(days= 0.8)
    event_date = now_date - datetime.timedelta(days= 2.4)
    available_date = event_date + datetime.timedelta(days=-2)
    expire_date = event_date + datetime.timedelta(days=2)
    print("Importing data...")

    for line in f:
        data = line.rstrip('\r\n').split(CSV_DELIM)
        # For demonstration purpose action names are taken from input along with secondary actions on
        # For the UR add some item metadata

        if data[0] != "$set":
            # nav-event,user-id,nav-id,true | false
            # def create(self, event_id, event, entity_type, entity_id,
            #      target_entity_type = None, target_entity_id = None, properties = None,
            #      event_time = None, creation_time = None)

            if data[3] == "true":
                converted = True
            else:
                converted = False

            client.create(
                event_id="1",
                event=data[0],
                entity_type="user",
                entity_id=data[1],
                target_entity_type="item",
                target_entity_id=data[2],
                event_time=current_date,
                properties={"converted": converted}
            )
            print("Event: " + str(data))
        elif data[0] == "$set":
            if data[1] == "user":
                props = data[3::]
                for prop in props:
                    propList = prop.split(PROPERTIES_DELIMITER)
                    propDict = {propList.pop(0): propList}
                client.create(
                    event="$set",
                    entity_type="user",
                    entity_id=data[2],
                    event_time=current_date,
                    properties=propDict
                )
                print("Event: " + str(data))

        count += 1
        current_date += event_time_increment
    client.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description="Import sample data for recommendation engine")
    parser.add_argument('--engine_id', default='test_ur_nav_hinting')
    parser.add_argument('--url', default="http://localhost:9090")
    parser.add_argument('--input_file', default="./data/ur_nav_hinting_handmade_data.csv")
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

    import_events(events_client, args.input_file)
