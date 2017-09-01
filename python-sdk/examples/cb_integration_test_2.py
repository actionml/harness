"""
Import sample data for recommendation engine
"""

import actionml
import argparse
import random
import datetime
import pytz

# import time

"""
example for importing events through the POST /engines/resource-id/events endpoint
taken from the PIO UR import_handmade.py, which imports from a csv
Todo: may want to use JSON for the CB
"""
CSV_DELIM = ","
PROPERTIES_DELIMITER = ":"
SEED = 0xdeadbeef


def import_events(client, file):
    f = open(file, 'r')  # json or csv? setup for csv for now
    # event name, user-id, test group, variant, conversion bool
    # page-view-conversion,user_1,1,1,true
    # {"event":"page-view-conversion","entityType":"user","entityId":"user_1","targetEntityType":"variant","targetEntityId":"1","properties":{"converted":true,"testGroupId":"1"},"eventTime":"2017-06-02T16:13:00.203+05:30","creationTime":"2017-06-02T10:43:14.156Z"}
    # #set,testGroup,group-id,start-date,end-date,pageVariantsList
    # {"event":"$set","entityType":"testGroup","entityId":"1","properties":{"testPeriodStart":"2017-06-02T00:00:00.000+05:30","pageVariants":["1","2"],"testPeriodEnd":"2017-10-10T00:00:00.000+05:30"},"eventTime":"2017-06-02T16:05:51.832+05:30","creationTime":"2017-06-02T10:36:05.425Z"}
    #
    # $set, object type, property name:value, property name: value
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

    # hardcode setup test
    # {"event":"$set","entityType":"testGroup","entityId":"1","properties":{"testPeriodStart":"2017-06-02T00:00:00.000+05:30","pageVariants":["1","2"]},"eventTime":"2017-06-02T16:05:51.832+05:30","creationTime":"2017-06-02T10:36:05.425Z"}
    client.create(
        event="$set",
        entity_type="group",
        entity_id="1",
        event_time=current_date,
        creation_time=current_date,
        properties={"pageVariants": ["1", "2"],
                    "testPeriodStart": "2017-06-02T16:05:51.832+05:30"
                    }  # no test period end
    )
    # time.sleep(0.5)

    for line in f:
        data = line.rstrip('\r\n').split(CSV_DELIM)

        if data[0] != "$set":
            # page-view-conversion,user_1,1,1,true
            # def create(self, event_id, event, entity_type, entity_id,
            #      target_entity_type = None, target_entity_id = None, properties = None,
            #      event_time = None, creation_time = None)

            # print("CSV line: " + str(data))
            if data[4] == "true":
                converted = True
            else:
                converted = False

            client.create(
                event_id="1",
                event=data[0],
                entity_type="user",
                entity_id=data[1],
                target_entity_type="variant",
                target_entity_id=data[3],
                event_time=current_date,
                creation_time=current_date,
                properties={"converted": converted, "testGroupId": data[2]}
            )
            print("Event: " + str(data))
            # time.sleep(0.5)

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
                # time.sleep(0.5)

        count += 1
        current_date += event_time_increment


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
        # time.sleep(0.5)

        print("Result: \"\nuser\": " + data[0] + response.json_body)
        count += 1


if __name__ == '__main__':

    user_id = '8c24137d-24ed-498d-a5d6-f21051db9ce5'
    user_secret = 'sILCF14ljZZdnqbzzg9SDJemz6wDY3YMH1rdbZ4r7RLzsA2vIh5R9H68WSMJN6ry'

    parser = argparse.ArgumentParser(
        description="Import sample data for recommendation engine")
    parser.add_argument('--engine_id', default='test_resource')
    parser.add_argument('--engine_id_2', default='test_resource_2')
    parser.add_argument('--url', default="http://localhost:9090")
    parser.add_argument('--events_file', default="./cb_events.csv")
    parser.add_argument('--events_file_2', default="./cb_events_2.csv")
    parser.add_argument('--queries_file', default="./cb_queries.csv")

    args = parser.parse_args()
    print(args)

    print("Events for " + args.engine_id_2)
    events_client = actionml.EventClient(
        engine_id=args.engine_id_2,
        url=args.url,
        threads=5,
        qsize=500,
        user_id=user_id,
        user_secret=user_secret)
    print(events_client.host)

    import_events(events_client, args.events_file_2)
    events_client.close()

    print("Events for " + args.engine_id)
    events_client = actionml.EventClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500,
        user_id=user_id,
        user_secret=user_secret)
    print(events_client.host)

    import_events(events_client, args.events_file)
    events_client.close()

    # Both resources trained, now query
    print("Queries for " + args.engine_id)
    query_client = actionml.QueryClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500,
        user_id=user_id,
        user_secret=user_secret)

    execute_queries(query_client, args.queries_file)
    query_client.close()

    print("Queries for " + args.engine_id_2)
    query_client = actionml.QueryClient(
        engine_id=args.engine_id_2,
        url=args.url,
        threads=5,
        qsize=500,
        user_id=user_id,
        user_secret=user_secret)

    execute_queries(query_client, args.queries_file)
    query_client.close()
