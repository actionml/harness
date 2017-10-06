# Navigation Hinting

This Harness Template tracks user navigation events for some site and recommends the link that most quickly has led other users to a conversion. If any particular user follows each hinted link they will be follow the most likely User Journey to a conversion opportunity. This is meant to allows the user to see information on the site that leads to more conversions and is meant to give lift to conversions.

The algorithm is simple and comes in 2 flavors:

 - **Non-personalized**: this will work the best until there is enough data from the site and user to make personalized hints and so should be used as the fallback for personalized hints and therefore the default for all sites. The algorithm tracks some number of nave events for all users until a conversion happens. The collected events define users's journey to conversion. These are used to predict the most likely journey to conversion from the page being viewed. More details under **Algorithm**
 - **Personalized**: this works like non-personalized but uses the journeys of users that are similar in behavior (nav events) and User metadata (profiles, demographic data, etc) to the particular user viewing a page. The hints are then based on what similar users have done and therefore are personalized. More details under **Algorithm**

The Engine provides Application or web site owners a way to nudge a user along a proven successful path to conversion. It adapts to changes in navigation on app or site automatically given time. If the app has a major redesign the Engine can be reset to start fresh.

## Algorithm

User navigation events are collected in a queue of fixed but configurable length until a conversion occurs. Then this "journey" is treated as a training journey. Meaning it is used to create the model of successful journeys. The queue events are down-weight based on how old they are. This is configurable but the simplest/default method uses a decay function to weight the event with 1/(number of nav events till conversion). In other words the further the nav event is from the conversion, the lower it will be weighted in importance to the conversion. The number of events collected means that some are dropped if no conversion happens for a long time so those dropped events are assumed to have nothing to do with the conversion.

Periodically the Engine takes all conversion paths and refreshes the model so there is no need to explicitly "train" the model. The model is created by summing all conversion vectors and ranking the resulting aggregate path vector. When a user navigates to a page the Engine is queried with a list of "eligible" links. The Hinting Engine returns the highest weighted eligible link. This will result in an arbitrary number of pages with no hints if they do not appear in the conversion journeys. 

There are other possible weighting decay functions that can be tested with cross-validation from site data including:

 - **Clicks to Conversion** this is the default if not specified and is simply 1/(number of clicks to conversion), so (1, 0.5. 0.3333, 0.25,...) This method does not use the timestamp of events only the order of the sequence.
 - **Exponential Decay** better know as the half-life method. This uses the time stamps of each event and decays the weight by ![](images/half-life-equation.png) "t" is the length of time until conversion, "&lambda;" is the [decay constant](https://en.wikipedia.org/wiki/Exponential_decay). The larger &lambda; the faster the decay. This defaults to &lambda;=1.0.
 - **Time to Conversion** this uses the time of the event to decay its weight as 1/(time till conversion). The time is expressed in seconds and is calculated as a duration at the time a user converts. In other words it is inverse of the number of seconds from the occurrence of the event until the user converts.

The model generated will be the sum of all weighted conversion vectors, ranked by summed weight. When a query is made, the highest weighted eligible navigation link is recommended.

## Personalized Algorithm

The Personalized version is identical to the non-personalized and has the same configurable decays and other parameters. The difference is that no one model is created. The model for a particular user is based on picking conversion journeys of "similar" users. These are weighted with the decay function and ranked. 

Here "similar" for similar users we us cosine similarity based on user attributes and current event history. The 2 factors can be weighted differently. Since user behavior (in the events) is known to be generally better than profile type attributes, the weighting of the 2 favors event similarity. The weighting of the 2 components of "similarity" is configurable.

To calculate this similarity quickly in realtime a cosine k-nearest-neighbor engine is used in the form of Elasticsearch. It maintains all converted User attributes and events so one query is made to return the users who will contribute to the personalized hinting model. In other words the user getting the hint will get a hint based on similar users. 

# Input

Input comes in the form of an Event stream via the Harness REST API for POSTing events in JSON form. Insert events in JSON form like so: 

```
curl -i -X POST http://localhost:9090/engines/<engine-id>/events \
-H "Content-Type: application/json" \
-d $JSONHERE
```

This assumes the server is listening on localhost:9090, the default, and the Navigation Hinting Engine is running on `/engines/<engine-id>`. As with any Harness Engine, if TLS/SSL and Auth are turned on using the Java or Python SDK is recommended.

## Navigation Events

All user navigation events can be thought of as (user-id, nav-id, conversion, time) but they must be encoded into "events" of the form:

```
{
    "event" : "nav-event",
    "entityType" : "user",
    "entityId" : "pferrel",
    "targetEntityId" : "nav-id",
    "properties" : {
        "conversion": "conversion-id" // omit for no conversion
    },
    "eventTime": "ISO-8601 encoded string"
}
```

 - **event**: this must be named "nav-event"
 - **entityType**: this must be "user"
 - **entityId**: this is a user-id
 - **targetEntityId**: this should be a nav-id
 - **eventTime**: ISO-8601 encoded string for the time of the event.
 - **properties**: defined as...
  - **conversion**: string that ids the conversion type. For instance, "account-creation", or "newsletter-signup", etc. If there is no conversion for the nav event, the field should be omitted.

## User Attributes

User Attributes are only used in the Personalized algorithm. The user attributes can consist of any number of features, which do not need to be specified in advance. However, for the attributes to have an effect, use the same property names and value sets whenever possible within a single Engine instance. This will increase the likelihood that we can find "similar users" by attribute.

The user attributes must be set independently of usage events. Note that all properties have a name and an array of strings, even if there is only one string.

```
{
  "event" : "$set",
  "entityType" : "user"
  "entityId" : "pferrel",
  "properties" : {
    "gender": ["male"],
    "location" : ["USA-postal-code-98119"],
    "anotherContextFeature": ["A", "B"]
  }
}
```

## Navigation Hinting Query

On the `POST /engines/<engine-id>/queries` endpoint the Engine will respond to the JSON query

The NH Engine can optionally make personalized queries. A simple example using curl for personalized recommendations is:

```
curl -H "Content-Type: application/json" -d '
{
  "user": "pferrel", // optional for non-personalized
  "eligibleNavIds": ["nav-1", "nav-34", "nav-49", "nave-11", "nav-3004". "nav-4098", ...]
}' http://localhost:9090/engines/<engine-id>/queries
```

This will get recommendations for user: "pferrel", These will be returned as a JSON object looking like this:

```
{
  "navHint": ["nav-49",...]
}
```

Note that you can request a hint for a new or anonymous user by omitting the `"user"` field of the query, in which case the recommendation will be non-personalized.

# Configuration of Navigation Hinting

The NH Engine has a configuration file defined below. This defines parameters for for the Engine itself, the algorithm, and for Elasticsearch the KNN engine. The parameters effect how fast an NH engine instance will be able to calculate a query response so try the default params and understand the implications before increasing limits. In many case simply increasing values will not provide better results&mdash;for instance increasing `"numQueueEvents"` may only include very low weighted events that are unrelated to conversion but the cost of calculating the model may be high. So this is an example of higher cost for less value. To use default values, simply omit their definition.

```
{
  "engineId": "test_resource",
  "engineFactory": "com.actionml.templates.nh.NavHintingEngine",
  "algorithm":{
    "numQueueEvents": 50,
    "decayFunction": "clicks",
    "halfLifeDecayLambda": 1.0,
    "num": 1
  }
}
```

 - **engineId**: used for the resource-id in the REST API. Can be any URI fragment.
 - **engineFactory**: constructs the Engine and reads the parameters, must be as shown.
 - **algorithm**: params known only by the algorithm, which is a part of the Template definition.
  - **numQueueEvents**: number of events stored per user before a conversion. Older events are dropped once this limit is reached and newer ones are added.
  - **decayFunction**: Must be one of `"clicks"`, `"click-time"`, `"half-life"`. the `"clicks"` and `"click-times"` function needs no other parameters. 
  - **halfLifeDecayLambda**: defines how quickly the weight of the event diminishes via the equation: ![](images/half-life-equation.png) This is only used if the decay function is `"half-life"`
  - **num**: how many of the highest ranking hints to return. Default = 1.
 
# Sharing Engine Users (todo: move to Harness docs)

Harness puts all engine data in a MongoDB database named with the engine-id. This is to allow easily dropping all data for an engine and to ensure that there is no leakage of data between engines. 

To allow engine instances to share users, we propose adding an engine specific setting to id the name for the user collection. This will never be dropped until the last engine referencing it is deleted from Harness. `$delete` events can be used to remove individual users.

New config for all engine instances:

```
"userCollectionName": "allUsersForSomeEngineSet"
```    

This allows any set of engines to share a collection of users by name so all engines in Harness may share the same set. Side effects:

 - if one instance of an engine is told to `$delete` the user, all will see the user dropped. This may not be ideal but is up to engines to manage.
 - conflicting `$set`/`$unset` of properties. One engine `$set`s a property and another `$unset`s the same property to get inconsistent results.
 - duplicate user properties become problematic, we may invent a new reserved event for `$merge` of new values into existing properties values as a sort of "upsert" to be used when `$set` would have the wrong semantics.

# Training

Harness was designed for streaming data sources and in the case of the NH engine will train for incoming events incrementally so all you need to do is send events and make queries. The models used for queries are eventually consistent with current input but are not necessarily consistent in real time. If User behavior events are used in the personalized version of the algorithm, these events will be in real time, only the model calculation may lag and this is not time critical. In most cases this lag will be negligible.

