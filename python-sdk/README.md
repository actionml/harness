# Harness Python SDK

The Python SDK is extended beyond what the Java SDK does because it also supports REST endpoints that execute the Harness CLI along with endpoints for sending events and making queries.

End users of Harness will likely us only the event and query APIs so they are described here.

**Note**: The Python SDK will shortly be renamed `harness` but currently is `actionml`.


# Installation

To install the module from PyPI, you may

    $ pip install actionml

or

    $ easy_install actionml

If you have cloned the repository and want to install directly from there,
do the following in the root directory of the repository:

    $ python setup.py install

This will install the "actionml" module to your Python distribution.


#Usage

To use actionml in your Python script, import the package, and create an `EventClient` and/or a `QueryClient`. They create flexible events similar to the PredictionIO event and queries. Outside of a few things that are standard virtually anything encodable in JSON can be passed in, see the Template/Engine documentation for specifics. Here are some examples for the Contextual Bandit.

**Events**:

    import actionml
    
    import datetime  # to get datetimes

    events_client = actionml.EventClient(
        engine_id="test_resource",
        url="localhost:9090",
        threads=5,  # more for async event creation
        qsize=500)    
        
    client.create(  # creates and sends a synchronous event request
        event="$set",
        entity_type="group",
        event_time=current_date, #  These are datetimes
        creation_time=current_date,
        properties={"pageVariants": ["1", "2"],
                    "testPeriodStart": event_date.isoformat()
                    # this is a string that the Engine must parse
                    # you can't use a datetime here
                    }
    )

**Queries**:   

    query_client = actionml.QueryClient(
        engine_id=args.engine_id,
        url=args.url,
        threads=5,
        qsize=500)


    result = query_client.send_query({"user": "user_1, "groupId": "1"})