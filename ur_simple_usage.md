# The Universal Recommender for Harness

The Universal Recommender runs on PredictionIO and Harness. Which version you use may depend on which Server platform you prefer. Also consider that virtually all future development will go into the Harness UR.

Algorithmically the Harness UR supports real time updates of item properties into the model, this supports real time business rules. PredictionIO is limited to updating business rules data only after training. This is important when rules like "only recommend in-stock items" are used since "in-stock" usually must be up-to-date in real time.

Most of the documentation for the PIO UR will apply to the Harness UR since they are input compatible. An export of UR data in PIO can be directly imported to the Harness UR.

## Harness UR vs PIO UR

The [design philosophy](design-philosophy-of-harness.md) is different for the 2 ML servers as well as config and REST APIs. The PIO event data can be sent to Harness but access APIs are different for the 2 (see the [Harness REST Spec](rest_spec.md).

The Algorithms for both versions are identical. The input and query payloads are identical&mdash;only the manner of connection and communicating with the 2 are different.

## The Correlated Cross-Occurrence (CCO) Algorithm

The algorithm, called Correlated Cross-Occurrence (CCO), allows many indicators of a user's preferences to be recorded and compared with the type of behavior that led other similar users to some recommended behavior. The CCO algorithm is multimodal, watching virtually any user behavior or indicator of preference and using this to make the best recommendations. See it described [here](http://actionml.com/blog/cco). An example of how using multimodal data can improve results see results of [experiments](https://developer.ibm.com/dwblog/2017/mahout-spark-correlated-cross-occurences/) done with simple "thumbs up" "thumbs down" type data.

## Business Rules

The CCO algorithm is implemented in a way that opens up a robust and flexible set of Business Rules to augment recommendations. These rules allow applications to include, exclude, or boost recommendations by user defined properties. The rules support "anding" and "oring" of rulesets. Boosting can be seen as "favoring" or "disfavoring" certain recommendations based on their properties. These are useful to express things like: "give me recommendations personalized for User-1 but only ones that are in-stock and boost/favor recommendations that are from the electronics category". Combining rules and tuning them to the context of recommendations is required in most application settings.


# Configuration

There are many way to tweak the internal parameters of the UR but to start with it is good to use simple ones since the defaults are chosen to perform well in most situations.

The minimal UR config defines "indicators" that correspond to data about users in every case. The data starts with the "primary indicator" which corresponds to the action you want to recommend&mdash;buy, watch, read, view, etc. Then the "secondary indicators", which is a list of other events that we think give some hint about the user's preferences&mdash;search, detail-view, favorite, share, thumbs-down, category-view, tag, the list is limited only by what information is available about users.

## Simple Configuration

The only required parameters for the UR are shown below.

```
{
  "engineId": "<some-engine-id>",
  "engineFactory": "com.actionml.engines.ur.UREngine",
  "sparkConf": {
    "master": "local",
    "spark.driver-memory": "8g",
    "spark.executor-memory": "16g",
    "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
    "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
    "spark.kryo.referenceTracking": "false",
    "spark.kryoserializer.buffer": "300m",
    "es.index.auto.create": "true"
  },
  "algorithm": {
    "indicators": [
      {
        "name": "buy"
      },{
        "name": "detail-view"
      },{
        "name": "search-terms"
      }
    ],
  }
}
```

These setting can be changed to fit the dataset:

 - **engineId**: (required) this should be a unique id that identifies the Engine instance. It is used as a REST URI fragment and to id the database for collecting events and so should be URI encoded. A snake case string of lower case characters and numbers + _ (underscore) is best.
 - **engineFactory**: (required) this should not be changed.
 - **sparkConf**: (required) certain values may need to be changed, those are:
    - **master**: (required) this must point to the Spark master of the form "spark://some-ip:7070" or be "local" or other valid Spark master value.
    - **other Spark params**: the rest of the params are used to configure the Spark job that trains, which is the task that builds the UR model from the dataset. 
    - **Spark params required by the UR**: `spark.serializer`, `spark.kryo.registrator`, `spark.kryo.referenceTracking`, `spark.kryoserializer.buffer` should not be changed. The buffer in rare cases can be increased.
    - **params passed in to Spark Libs**: `"es.index.auto.create": "true"` is required to tell Elasticsearch to re-index a newly generated model. There are other params that will help tune the Elasticsearch library but they are not alway required (see advanced config)
 - **algorithm**: (required) these tune how the UR works. Most params have default values that work for many apps but at minimum indicator names must be specified:
    - **indicators**: (required) named indicator events, the first is considered the "primary" others are considered "secondary"
        - **name**: this is expected to be the value supplied in the JSON input `"event": "<some-event-name>"` for the rest of the input event format see Input in this document.

# Input

Input comes in events of 2 general types, indicators and property change events. Indicators are named in the config JSON, property changes are made with reserved event names like `$set` and `unset`.

The easiest way to send input (and make queries) is to use one of the Harness client libs (Java, Scala, Python) but for illustration purposes we will demo using `curl` to send small JSON payloads to the server.

## Indicator Events

Indicator events are always named in the Engine's JSON config. any event not named is ignored. Indicator contain 4 important pieces of information, the rest is boilerplate. Each event has (user-id, indicator-name, item-id, event-time). Using the simple config above they are formatted in this way:

```
{
   "event" : "buy",
   "entityType" : "user", // ALWAYS "user"
   "entityId" : "1243617", // user-id
   "targetEntityType" : "item", // ALWAYS "item"
   "targetEntityId" : "iPad", // item-id
   "eventTime" : "2015-10-05T21:02:49.228Z" // ISO8601 string
}
```

For a secondary indicator the item-id should match the type pf the indicator so for "search-terms" each term the user searches for could be input as:

```
{
   "event" : "search-terms",
   "entityType" : "user",
   "entityId" : "1243617",
   "targetEntityType" : "item",
   "targetEntityId" : "apple",
   "eventTime" : "2015-10-05T21:02:49.228Z"
}
```

Notice that the only thing that changes is the user-id, indicator-name, item-id, and event-time. 

To send these input events to Harness using `curl` we reference the [Harness REST API](rest_spec.md):

```
curl -H "Content-Type: application/json" -d '
{
   "event" : "buy",
   "entityType" : "user", // ALWAYS "user"
   "entityId" : "1243617", // user-id
   "targetEntityType" : "item", // ALWAYS "item"
   "targetEntityId" : "iPad", // item-id
   "eventTime" : "2015-10-05T21:02:49.228Z" // ISO8601 string
}' http://<harness-server>:9090/engines/<some-engine-id>/events
```

for experiments using a server installed locally and an engine-id of `test_ur` we have:

```
curl -H "Content-Type: application/json" -d '
{
   "event" : "buy",
   "entityType" : "user", // ALWAYS "user"
   "entityId" : "1243617", // user-id
   "targetEntityType" : "item", // ALWAYS "item"
   "targetEntityId" : "iPad", // item-id
   "eventTime" : "2015-10-05T21:02:49.228Z" // ISO8601 string
}' http://localhost:9090/engines/test_ur/events
```

## Property Changes

For the UR properties are always expressed as item attributes. User properties like profile data can be input using indicators but see Advanced Input for more description.

Setting a property for an item we use the reserved event `$set`:

```
curl -H "Content-Type: application/json" -d '
{
   "event" : "$set",
   "entityType" : "item",
   "entityId" : "iPad",
   "properties" : {
      "category": ["Electronics", "Mobile Devices"],
      "expireDate": "2019-10-05T21:02:49.228Z"
      "product-type":["tablet"],
      "colors":["space gray", "silver", "rose gold"],
      "brand":["Apple"],
      "available":["2018"]
   }
   "eventTime" : "2018-12-05T21:02:49.228Z"
}' http://<harness-server>:9090/engines/<some-engine-id>/events
```

Properties may be sent all at once (as above) or one at a time. They are immediately attached to items in the model so they can affect results through Business Rules

## Property Types

The UR supports:

 - **Categorical Properties**: These have a name associated with a JSON array of strings. Each string will id some categorical value that is attached to the item. In queries we will use them in rules. Categorical properties can be though of as tags, zero or more values can be associated with any named categorical property. The fact that they are encoded as an array of strings tells the UR to use them in this way.
 - **Dates**: Certain proscribed uses of dates are allowed. See Advanced Queries for their use. These are encoded as JSON string values corresponding to IOS8601 formatted datetimes. Any property encoded as a string (as opposed to an array of strings) is assumed to be a date.

# Queries

Queries are made to the UR (after training has produced a model) using the Harness Client SDK or directly through a REST JSON payload much as events are set to the UR.

A Simple user-based recommendations query looks like:


```
curl -H "Content-Type: application/json" -d '
{
  "user": "User-1"
}' http://<harness-server>:9090/engines/<some-engine-id>/queries
```

An item-based recs query looks like:
```
{
  "item": "iPad"
}
```

This returns "people who liked the iPad also like these other items". This query could possibly return items that are "off subject" meaning items that are seemingly unrelated. To narrow down the recommendations we might apply a simple Business Rule:

```
{
  "user": "xyz"
  "rule": [
    "name": "brand",
    "values": ["Apple"],
    "bias": 20
  ]
}
```

If item properties have already been used to associate `"brand"` values with items, then the query above will boost the score for any item-based recommendation by 20 times before ranking the results. This is a long way of saying Apple items will be favored, highly in results.

The `"bias"` can be used to include, exclude or boost (as in the example above). A `bias" of 0 means to exclude any recommendations that match the rule, -1 means to include ONLY those that match one or more of the rules. 