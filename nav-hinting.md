# Navigation Hinting

This Harness Template tracks user navigation events for a site or app and recommends the navigation id that most is most likely to lead to a conversion. These ids can be thought of as conversion hints and are typically used to emphasize some navigation in the UX.

## UX Recommendations

To make the UX uncluttered and more self explaining:

 - It is recommended to exclude hinting from common navigation elements like menus, sidebars, footers, and mobile app navigation placeholders&mdash;otherwise they may be hinted all the time as much more common paths to conversions.
 - Hint a limited number of nav ids (one?) even though more than one may be returned.
 - When possible a hover tool-tip may be useful to describe the hint purpose.

## Algorithm

The algorithm is simple and comes in 2 flavors:

 - **Non-personalized**: this shows the most popular nav event for all users who convert. It will work best until there are enough conversions to differentiate personal journeys to conversion.
 - **Personalized**: this works like non-personalized but find conversion journeys most similar to the journey of the current user. **Note**: this is planned for Hinting V0.2.0 

The Engine provides application or web site owners a way to nudge a user along a proven successful path to conversion. It adapts to changes in navigation on app or site automatically given time. If the app has a major redesign the Engine can be reset to start fresh.

## Algorithm Details for Non-Personalized Hints

User navigation events are collected in a queue of fixed but configurable length until a conversion occurs. Then this "journey" is treated as a training journey. Meaning it is used to create the model of successful journeys. The queue events are down-weighted based on how old they are. This "decay function" is configurable. The simplest/default method uses nav event ordering with 1/(number of nav events till conversion). In other words the further the nav event is from the conversion, the lower it will be weighted in importance to the conversion. The number of events collected means that some are dropped if no conversion happens for a long time so those dropped events are assumed to have nothing to do with the conversion.

Periodically the Engine takes all conversion paths and refreshes the model so there is no need to explicitly "train" the model. The model is created by summing all conversion vectors and ranking the resulting aggregate path vector. When a user navigates to a page the Engine is queried with a list of "eligible" links. The Hinting Engine returns the highest weighted eligible link. This will result in an arbitrary number of pages with no hints if they do not appear in the conversion journeys. 

## Decay Functions

There are other possible weighting decay functions that can be tested with cross-validation from site data including:

 - **Clicks to Conversion** this is the default if not specified and is simply 1/(number of clicks to conversion), so (1, 0.5. 0.3333, 0.25,...) This method does not use the timestamp of events only the order of the sequence.
 - **Exponential Decay** better know as the half-life method. This uses the time stamps of each event and decays the weight by ![](images/half-life-equation.png) "t" is the length of time until conversion, "&lambda;" is the [decay constant](https://en.wikipedia.org/wiki/Exponential_decay). The larger &lambda; the faster the decay. This defaults to &lambda;=1.0.
 - **Time to Conversion** this uses the time of the event to decay its weight as 1/(time till conversion). The time is expressed in seconds and is calculated as a duration at the time a user converts. In other words it is inverse of the number of seconds from the occurrence of the event until the user converts.

The model generated will be the sum of all weighted conversion vectors, ranked by summed weight. When a query is made, the highest weighted eligible navigation ids are recommended. Here **eligible** nav events are specified with each query by enumerating nav events that can be hinted by the app.

## Personalized Algorithm (Hinting v0.2.0 Planned)

The Personalized version is similar to the non-personalized and has the same configurable decays and other parameters. The decay function here acts as a threshold to detect events that are too old to consider. This threshold is applied to both the user's current journey and the data used to calculate the cooccurrence model. After passing this threshold LLR is used to find the most likely nav events correlate with a conversion.

The model uses cooccurrence to calculate the most likely conversion links and so is based on the user's current journey history. The hints for a particular user are based on conversion journeys of "similar" users. 

To calculate this similarity quickly in realtime a cosine k-nearest-neighbor engine is used in the form of Elasticsearch in a manner similar to the a simplified CCO algorithm as used in the Universal Recommender with TTL (time to live) applied to all input to model calculation and user history. The TTL is based on the decay function. 

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

User Attributes are not used in the non-personalized or in the personalized.

## Navigation Hinting Query

On the `POST /engines/<engine-id>/queries` endpoint the Engine will respond to the JSON query

```
curl -H "Content-Type: application/json" -d '
{
  "user": "pferrel", // optional and ignored for non-personalized
                     // this is required for personalized hints
  "eligibleNavIds": ["nav-1", "nav-34", "nav-49", "nave-11", "nav-3004". "nav-4098", ...]
}' http://localhost:9090/engines/<engine-id>/queries
```

This will get recommendations for user: "pferrel". These will be returned as a JSON object looking like this:

```
{
  "result": [{"nav-49": score-1},...]
}
```

**Note**: The `user` portion of the query only has an effect for personalized hinting, it is ignored otherwise.

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
 
# Training

Harness was designed for streaming data sources and in the case of the NH engine will train for incoming events incrementally so all you need to do is send events and make queries. 

For Personalized Hinting a heavy weight background process can be triggered periodically that will not interrupt querying or input. This will use Spark and Elasticsearch.
