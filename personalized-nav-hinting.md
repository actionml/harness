# Personalized Navigation Hinting

This Harness Template tracks user navigation events for a site or app and recommends the navigation id that most is most likely to lead to a conversion. These ids can be thought of as conversion hints and are typically used to emphasize some navigation link in the UX.

Personalization is accomplished by using the user's most recent behavior (of several forms) to hint the nav-id most likely to appeal to the individual user.

## Consuming UX Recommendations

To make the UX uncluttered and more self explaining:

 - It is recommended to exclude hinting from common navigation elements like menus, sidebars, footers, and mobile app navigation placeholders&mdash;otherwise they may be hinted all the time as much more common paths to conversions.
 - Hint a limited number of nav ids (one?) even though more than one may be returned.
 - When possible a hover tool-tip may be useful to describe the hint purpose.

## Algorithm

The algorithm is called Correlated Cross-Occurrence (CCO) and is used in the general purpose Universal Recommender. It allows many indicators of a user's preferences to be recorded and compared with the type of behavior that led other similar users to convert. This is why it is called "personalized" since the hint is a personalized recommendation. 

The CCO algorithm is multimodal, watching virtually any user behavior or indicator of preference and using this to make the best recommendations. See it described [here](http://actionml.com/blog/cco)

This Engine provides application or web site owners a way to nudge a user along a proven successful path to conversion. It adapts to changes in navigation on app or site automatically given time. If the app has a major redesign the Engine can be reset to start fresh.

# Special Harness and Services Config for this Engine

Harness does not require any specific "compute engine" like Spark, TensorFlow, or other. This means each Engine is free to choose what Compute-Engine to use&mdash;picking the best for the algorithm. The Universal Recommender is written to use Spark and Elasticsearch, with an extra requirement of Apache Mahout. These will need special setup most of which is confined to the engine's JSON config file. But some things should be considered when starting up the Services:

## Spark

Spark uses the Hadoop Distributed File System (HDFS) and can store logs in it for failed Jobs, which is quite useful in debugging an Engine's config. Spark is somewhat unique in that a Job may fail based on the data it sees, not only based on whether the cluster fits the config. This means that some Spark conf will need to be tuned to the data seen in an Engine instance. 

We suggest setting Spark up to inherit the client config from the HDFS client installation and to log to HDFS.

**Inherit Hadoop Client Config**: After installing Spark copy `spark/conf/spark-env.sh.template` to `spark/conf/spark-env.sh` and editing it to add:

```
# Replace with the local path to hadoop's config files, usually 
# in <hadoop's root directory>/hadoop/etc/hadoop/
# Example
HADOOP_CONF_DIR=/usr/local/hadoop/etc/hadoop/

```

**Setup Spark Logging to HDFS**: This assumes HDFS is setup and running in cluster or psuedo-cluster (single machine) mode. Create a directory in HDFS where the user that has started Harness has permission to write files. For example `/user/your-user-name/spark-logs` would write to this directory if Harness was started by the user= "your-user-name". Set the following in the `sparkConf` section of the engine's config file

```
"spark.eventLog.enabled": "true",
"spark.eventLog.dir": "hdfs://<namenode IP address>/user/<your-user-name>/spark-logs:9000"
```

The `eventLog.dir` is the fully qualified URI for the directory and so should contain the namenode address (DNS or IP), the hdfs path, and port number (9000 unless expressly changed during HDFS setup).

If this is not setup the traces of Job execution will disappear as soon as the Job completes, even if it is a failure, so you may loose important debugging information. If it is setup the logs will accumulate forever and may fill up HDFS so periodic cleanup is important.

# Configuration of NavHintingUREngine

Configuration is especially important because it defines the type of events by name that will be gathered and used to predict a user's preferences.


```
{
  "engineId": "ur",
  "engineFactory": "com.actionml.engines.ur.UrNavHintingEngine",
  "sparkConf": {
    "master": "yarn",
    "deploy-mode": "cluster",
    "driver-memory": "4g",
    "executor-memory": "4g",
    "executor-cores": "1",
    "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
    "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
    "spark.kryo.referenceTracking": "false",
    "spark.kryoserializer.buffer": "300m",
    "es.index.auto.create": "true",
    "comment": "remove this because it will confuse the ES Spark lib is these nodes do not exist--example only",
    "es.nodes": "es-node-1,es-node-2"
  },
  "algorithm": {
    "esMaster": "es-node-1",
    "indexName": "urindex",
    "typeName": "items",    "numQueueEvents": 50,
    "blacklistEvents": [], // IMPORTANT, this must be an empty list
    "maxEventsPerEventType": 500,
    "maxCorrelatorsPerEventType": 50,
    "maxQueryEvents": 100,
    "num": 2,
    "indicators": [
      {
        "name": "nav-id"
      },{
        "name": "content-pref",
        "maxCorrelatorsPerItem": 50,
        "minLLR": 5.0
      },{
        "name": "search-terms"
      }
    ],
    "num": 1
  }
}
```

## Engine Parameters

 - **engineId**: used for the resource-id in the REST API. Can be any URI fragment.
 - **engineFactory**: constructs the Engine and reads the parameters, must be as shown.

## Spark Parameters (sparkConf)

For the most part these correspond directly to parameters that can be set in the Spark context. For example see the documented [Spark Properties](https://spark.apache.org/docs/latest/configuration.html#spark-properties). 

In other cases they are needed as key->value pairs so that libraries that use spark can read the parameters they need. The Elasticsearch params that start with "es." are this type. These are documented on the Elasticsearch site. The common ones you might need are:

 - **es.nodes**: a comma separated list of node names or ip addresses like "host1,host2" or "1.2.3.4,1.2.3.5,1.2.3.6". The default port for ES REST is 9200 so if you are using that there is no need to specify it.
 - **es.port**: defaults to 9200 but can be changed with this config if needed.
 - **es.net.http.auth.user** and **es.net.http.auth.pass** allow you to set username and password if you need to use Elasticsearch with authentication.
 - **es.index.auto.create**: should always be set to "true"

## CCO Algorithm Parameters

The Algorithm: params section controls most of the features of the CCO. Possible values are:

 - **indexName**: optional, defaults to the URL encoded version of the Engine ID. To specify a different index in Elasticsearch for storage of the CCO model enter a string. The Elasticsearch URI for its REST interface is http:/**elasticsearch-machine**/indexName/typeName/... You can access ES through its REST interface here.
 - **typeName**: optional, defaults to "items". Describes the type in Elasticsearch terminology and is used in the REST access to the model.
 - **numESWriteConnections**: optional, default = number of threads in entire Spark Cluster, which may overload ES. This is used when the model is written to ES after it is calculated using Spark. If you see task failures but due to retries, lowering this may help remove the errors. The other ideal option is to add to / scale out your ES cluster because lowering the number of connections will slow the Spark cluster down by reducing the number of tasks used to write to ES. The rule of thumb for this is (numberOfNodesHostingPrimaries * bulkRequestQueueLength) * 0.75. In general this is (numberOfESCores * 50) * 0.75. For ES 1.7 the bulk queue is defaulted to 50
 - **maxEventsPerEventType**: optional (use with great care), default = 500. Amount of user history to use in model calculation.
 - **maxCorrelatorsPerEventType**: optional, default = 50. this applies to all event types, use indicators to apply to specific event types—called indicators. An integer that controls how many of the strongest correlators are created for every event type named in eventNames.
 - **indicators**: The first name in the array should be "nav-hint" then the rest can be other event names that correspond to indicators of user preferences. This method for naming event types also allows for setting downsampling per event type. These are more properly called "indicators" because they may not be triggered by events but are always assumed to be something known about users, which we think "indicates" something about their preferences:
    - **name**: name for the indicator, as in eventNames.
    - **maxCorrelatorsPerItem**: optional, default = 50. The number of correlated items per recommended item or put another way the number of this type of item stored in the model. This is set to give best results for the indicator type and should be significantly less than the total possible number of the named indicator.
    - **minLLR**: this is not used by default and is here when an LLR score is desired as the minimum threshold. Since LLR scores will be higher for better correlation this can be set to ensure the highest quality correlators are the only ones used. This will increase precision of recommendations but may decrease recall, meaning you will get better recommendations but less of them. A rule of thumb would say to use something like 5 for a typical high quality ecom dataset.
    - **maxQueryEvents**: optional, default = 100. An integer specifying the number of most recent user history events used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests.
 - **num**: optional, default = 20. An integer telling the engine the maximum number of recommendations to return per query but less may be returned if the query produces less results or post recommendations filters like blacklists remove some.
 - **blacklistEvents**: optional, default = the primary action. **Note: for nav-hinting this should be set to an empty array.**

# Input

Input comes in the form of an Event stream via the Harness REST API for POSTing events in JSON form. Insert events in JSON form like so: 

```
curl -i -X POST http://localhost:9090/engines/<engine-id>/events \
-H "Content-Type: application/json" \
-d $JSONHERE
```

This assumes the server is listening on localhost:9090, the default, and the Navigation Hinting Engine is running on `/engines/<engine-id>`. As with any Harness Engine, if TLS/SSL and Auth are turned on using the Java or Python SDK is recommended.

## Indicator Events

User navigation events are not the only type of input allowed. They are, in a sense, the most important because they define when an event has led to a conversion and so define when a user's data should be used to calculate the next model built during `harness train <engine-id>`.

## The Primary Event&mdash;**nav-event**

```
{
    "event" : "nav-event",
    "entityType" : "user",
    "entityId" : "pferrel",
    "targetEntityId" : "nav-id",
    "properties" : {
        "conversion": true | false
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
  - **conversion**: true of false, false if omitted.
 - **eventTime**: ISO-8601 encoded string. This is a required field.

When the first conversion is seen for a particular `nav-id` a model is started, which can later be deleted using the same `nav-id`, though it is later called a `conversion-id` in the `target-delete` event.

## Secondary Indicators

Other indicators of user preference are defined in the `indicators` section of the Engine's config. They will be named to show what they indicate like `content-pref` or `search-terms` and are checked for correlation with the primary event to produce the CCO model. They can be named anything and triggered when a user expresses some preference. These are formed like the `nav-event` but with a different `event` name and `targetEntityId` like so:

**contextual-tags** preference for a contextual tag

```
{
    "event" : "content-pref",
    "entityType" : "user",
    "entityId" : "pferrel",
    "targetEntityId" : "tag1",
    "eventTime": "ISO-8601 encoded string"
}
```

Note that one event per tag is sent. (It is possible an array of events may be sent in one POST in a future version of Harness)

**search** triggered when users search, one term per event. The terms should be analyzed or stemmed to produce the closest possible token that indicates the root meaning. In English words like "colorful", "colors", and "color" have very similar root meaning so all would be analyzed down to the token "color". There are also grammatical words that hold very little meaning in terms of search and these are removed in a process called "stop words". A library for analysis is provided for many languages in the Apache Lucene project. See the tutorial [here](https://www.toptal.com/database/full-text-search-of-dialogues-with-apache-lucene) for a basic look at the Lucene analysis pipeline.

```
{
    "event" : "search-terms",
    "entityType" : "user",
    "entityId" : "pferrel",
    "targetEntityId" : "color",
    "eventTime": "ISO-8601 encoded string"
}
```    

**user-attributes**: User attributes are similar to `content-pref` indicators. If input in the same manner they will be checked for correlation with the primary indicators and be added to the model if they pass the test. The example is "gender" which can be encoded as:

```
{
    "event" : "content-pref",
    "entityType" : "user",
    "entityId" : "pferrel",
    "targetEntityId" : "male",
    "eventTime": "ISO-8601 encoded string"
}
```

There is no special meaning for "content-pref" so combining user attributes with these makes as much sense as anything else.

**other**: other indicators may be identified and sent in named events as needed.


# Controlling Input (non-events)

**`$delete` Models**

The `nav-id` where `"conversion": true` is here referred to as the `conversion-id` and it identifies the Model associated with a particular conversion target. When the target is added, the Nav-Hinting Engine does not need to know immediately. The model for a particular `conversion-id` will be created on the first conversion and updated on every subsequent conversion. When the conversion target is deleted, its model can be removed so that no hints will be made for that conversion target. Send an event like this:

```
{
    "event" : "$delete",
    "entityId" : "conversion-id", <-- taken from conversion nav-event
    "entityType" : "model",
    "eventTime": "ISO-8601 encoded string"
}
```

This will delete the model for the conversion-id so none of the hints will be for this target.

**`$delete` Users**

As with other Engines to delete any Journey information (not the model, which remains unaffected) attached to a user you should `$delete` the User with:

    {
        "event" : "$delete",
        "entityId" : "pferrel",
        "entityType" : "user",
        "eventTime": "ISO-8601 encoded string"
    }

Since the number of Users will increase without bounds some external method for trimming them will eventually be required. Since old inactive users will not generally have meaningful Journeys is they have not converted in a long time, a TTL could be employed if the user's journey has not been modified in some period of time. The Nav Event will re-create the user later if they later become active again. 

## User Attributes (Non-Personalized)

User Attributes are not used in the non-personalized hinting. The user-id is only used as a key to the journey they are on. Once a user converts, their journey contributes to the model predicting how likely the journey will lead to a conversion.

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

This will get recommendations for user: "pferrel" but since NavHinting v0.1.0 is non-personlized the user-id will not affect the results. 

```
{
  "result": ["nav-49",...]
}
```

**Note**: The `user` portion of the query only has an effect for personalized hinting, it is ignored otherwise.
 
# Training

Harness was designed for streaming data sources and in the case of the NH non-personalized engine will train for incoming events incrementally so all you need to do is send events and make queries. 

For Personalized Hinting a more heavy weight background process can be triggered periodically that will not interrupt querying or input. This will use Spark and Elasticsearch and is targeted for NH v0.2.0.
