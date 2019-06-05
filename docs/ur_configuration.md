# Configuration

Engines in Harness follow a pattern that defines defaults for many parameters then allows you to override them in the Engine's JSON config. If further refinement makes sense it is done in the Query. 

For instance The default number of results returned is 20, this can be overridden in the UR config JSON, which can later be overridden in any query.

Business Rules can also be specified in the Engine's config or in the query. The use case here might be to only include items where `"available": "true"` and this should be used in every query unless the Query overrides or add new rules.

## Configuration Sections

The UR Configuration is written in [Harness JSON](harness_json.md) (JSON extended to allow substitution of values with data from environmental variables) and divided into sections for:

 - **Engine key-value pairs** The settings outside of a named section that are required or may be used in any engine.
 - **`dataset`** params that apply to input data encoded as events
 - **`algorithm`** params that control the behavior of the UR algorithm, known as Correlated Cross-Occurrence (CCO). The Algorithm section also can hold default Query parameters to be used with all Queries unless overridden in a specific Query.
 - **`sparkConf`** params are passed into the Spark Job. These are needed because Spark jobs often require settings to be passed in to Spark Workers via a data structure called `sparkConf`. For instance the Elasticsearch library that writes a Spark RDD to ES needs several settings that it gets from the `soarkConf`. This section is the mostlikely place to put extended JSON that reads from `env`.

## Simplest UR Configuration

Imagine an ECom version of the UR that only watches for "buys" and product detail "views". To be sure there are many other ways to use a recommender but this is a good, simple example.

We will make heavy use of default settings that have been chosen in the Universal Recommender code and only set required config and parameters.

```
{
    "engineId": "ecom_ur",
    "engineFactory": "com.actionml.engines.ur.UREngine",
    "sparkConf": {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
        "spark.kryo.referenceTracking": "false",
        "spark.kryoserializer.buffer": "300m",
        "spark.executor.memory": "20g",
        "spark.driver.memory": "10g",
        "spark.es.index.auto.create": "true",
        "spark.es.nodes": "elasticsearch-host",
        "spark.es.nodes.wan.only": "true"
    },
    "algorithm":{
        "indicators": [ 
            {
                "name": "buy"
            },{
                "name": "view"
            }
        ],
    }
}
```

Here we are telling Harness how to create a UR instance and telling the UR Instance what types of input to expect. **NOTE**: the first indicator is the ***primary*** one, when it comes in as an input Event it has item-ids that will be recommended. The secondary indicator will also come in as an input Event and will make the UR more predictive since it gives more information about user preferences. Secondary indicators do not have to come in with the same item-ids as the primary so maybe it is easier to send a page-id than a product-id (sent with the "buy" Events). The secondary indicator will be just as helpful.

Depending on the size of your data this config might work just fine for an ECom application and if the dataset size grows too large we just increase memory given to Spark.

*It is highly recommended that you start with this type of config before tuning the numerous values that may (or may not) yield better results.*
 
## Complete UR Engine Configuration Specification

How to read config settings:

 - "\<some-value\>" replace with your value
 - "this" \| "that" use "this" OR "that"
 - if no annotation is present the value must be set exactly as shown
 - keys should always be as used as quoted
 - most settings can be omitted if default values are sufficient, See Default UR Settings.


```
{
    "engineId": "<some-unique-id>",
    "engineFactory": "com.actionml.engines.ur.UREngine",
    "modelContainer": "</some/model/path>",
    "mirrorType": "localfs" | "hdfs",
    "mirrorLocation": "</some/mirror/path>",
    "dataset": {
        "ttl": "<356 days>",
    },
    "sparkConf": {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
        "spark.kryo.referenceTracking": "false",
        "spark.kryoserializer.buffer": "300m",
        "spark.executor.memory": "<4g>",
        "spark.es.index.auto.create": "true",
        "spark.es.nodes": "<node1>,<node2>",
        "spark.es.nodes.wan.only": "true"
    },
    "algorithm":{
        "indicators": [ 
            {
                "name": "<indicator-event-name>",
                "maxCorrelatorsPerItem": <some-int>",
                "minLLR": <some-int>,
                "maxIndicatorsPerQuery": <some-int>
            },
            ...
        ],
        "blacklistEvents": ["<list>", "<of>", "<indicator>", "<names>"],
        "maxEventsPerEventType": <some-int>,
        "maxCorrelatorsPerEventType": "<some-int>",
        "maxQueryEvents": <some-int>,
        "num": <some-number-of-results-to-return>,
        "seed": <some-int>,
        "recsModel": "all" | "collabFiltering" | "backfill",
        "expireDateName": "<some-expire-date-property-name>",
        "availableDateName": "<some-available-date-property-name>",
        "dateName": "<dateFieldName>",
        "userbias": <-maxFloat..maxFloat>,
        "itembias": <-maxFloat..maxFloat>,
        "returnSelf": true | false,
        “rules”: [
          {
            “name”: ”<some-property-name>”,
            “values”: [“value1”, ...],
            “bias”: -maxFloat..maxFloat,
          },
          ...
        ]
        "numESWriteConnections": 100,      }
    }
}
```

## Default UR Settings

 - REQUIRED the value must be set
 - NONE the value defaults to no setting, which tells the UR to not use the setting
 - RANDOM chosen randomly

```
{
    "engineId": REQUIRED,
    "engineFactory": "com.actionml.engines.ur.UREngine",
    "modelContainer": NONE,
    "mirrorType": NONE,
    "mirrorLocation": NONE,
    "dataset": {
        "ttl": "356 days",
    },
    "sparkConf": {
        "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
        "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
        "spark.kryo.referenceTracking": "false",
        "spark.kryoserializer.buffer": "300m",
        "spark.executor.memory": REQUIRED,
        "spark.driver.memroy": REQUIRED,
        "spark.es.index.auto.create": "true",
        "spark.es.nodes": "localhost",
        "spark.es.nodes.wan.only": "true"
    },
    "algorithm":{
        "indicators": [ 
            {
                "name": ONE OR MORE REQUIRED,
                "maxCorrelatorsPerItem": 50,
                "minLLR": NONE,
                "maxIndicatorsPerQuery": 100
            },
            ...
        ],
        "blacklistEvents": ["primary-indicator-name"],
        "maxEventsPerEventType": 500,
        "maxCorrelatorsPerEventType": 50,
        "maxQueryEvents": 100,
        "num": 20,
        "seed": RANDOM,
        "recsModel": "all",
        "expireDateName": NONE,
        "availableDateName": NONE,
        "dateName": NONE,
        "userbias": NONE,
        "itembias": NONE,
        "returnSelf": false,
        “rules”: [ NONE ]
        "numESWriteConnections": NONE,
    }
}
```

## Dataset Parameters

The `"dataset"` section controls how long to keep input data. 

 - **ttl**: this take a String value that describes the length of time before an indicator Event is dropped from the DB. This only affects indicators, `$set` type events (non-indicator reserved Events) change mutable objects in the DB and so do not accumulate. The `ttl` stands for "time-to-live". Optional, default "365 days".


## Algorithm Parameters

The `"algorithm"` section controls most of the tuning and config of the UR. Possible values are:

 * **indicators**: required. An array of string identifiers describing Events recorded for users, things like “buy”, “watch”, “add-to-cart”, "search-terms", even “location”, or “device” can be considered indicators of user preference. 

    The first indicator is considered the ***primary*** indicator, because it **must** exist in the data and is considered the strongest indication of user preference for items, the others enrich the URs understanding of user preferences. Secondary indicators/Events may or may not have item-ids that correspond to the items to be recommended (id that come with the primary indicator), so they are allowed to be things like category-ids, search-terms, device-ids, location-ids... For example: a "category-pref" indicator would have a category-id as the target entity id but a "buy" would have a product-id as the target entity id (see UR Events). Both work fine as long as all indicator events are tied to users. 
  * **name**: required. Name for the indicator Event. 
  * **maxCorrelatorsPerItem**: optional, default: 50. Number of correlated items per recommended item. This is set to give best results for the indicator type and is often set to less than 50 (the default value) if the number of different ids for this event type is small. For example if the indicator is "gender" and we only count 2 possible genders there will be only be 2 possible ids "M" and "F" so the UR will preform better if `maxCorrelatorsPerItem ` is set to 1, which would find THE gender that best correlates with a primary event (for instance a "buy"). Without this setting the default of 50 would apply, meaning to take the top 50 gender ids that correlate with the primary indicator/conversion item. With enough data you will get all genders to correlate, meaning none could differentiate recommendation, in turn meaning the indicator is providing no value. Taking 1 correlator would force the UR to choose which is more highly correlated instead of taking up to 50 of the highest. 
    
        A better approach is to use `minLLR` to create a correlation threshold but this is more difficult to tune.
    * **maxIndicatorsPerQuery**: optional (use with great care), default: 100. Amount of the most recent user history to use in recommendation model queries. Making this smaller than the default may capture more recent user preferences but may lose longer lived preferences.
    * **minLLR**: optional, default: NONE. This is not used by default and is here when an LLR score is desired as the minimum threshold. Since LLR scores will be higher for better correlation this can be set to ensure the highest quality correlators are the only ones used. This will increase precision of recommendations but may decrease recall, meaning you will get better recommendations but less of them. Increasing this may affect results negatively so always A/B test any tweaking of this value. There is no default, we keep `maxCorrelatorsPerItem` of the highest scores by defaultf&mdash;no matter the score. A rule of thumb would say to use something like 5 for a typical high quality ecom dataset.
* **maxQueryEvents**: optional (use with great care), default: 100. An integer specifying the number of most recent user history events used to make recommendations for an individual. More implies some will be less recent actions. Theoretically using the right number will capture the user’s current interests. This global value is overridden if specified by the indicator.
* **num**: optional, default: 20. An integer telling the engine the maximum number of recommendations to return per query but less may be returned if the query produces less results or post recommendations filters like blacklists remove some.
* **blacklistIndicators**: optional, default: the primary indicator. An array of strings corresponding to indicator names. If a user has history of any of these indicators and if the indicator has an item-id from the same items as the primary indicator then the item will not be recommended. This is used when trying to avoid recommending items that the user has seen or already converted on. In ECom this might mean; "do not recommend items the user 'buys' or 'views'". The default is to not recommend conversion items. If you want to recommend items the user has interacted with before, things they have bought for example, then set this value to an empty array: `[]` This will signal that no history should cause an item to be blacklisted fro recommendations.
* **rules**: optional, default: NONE. An array of Business Rules as defined for Queries (see [UR Queries](ur_queries.md). These act as defaults for every query and can be added to in any query. This is useful when you want to check something like `"instock": "true"` for every query but may add other rules at query time.
* **userBias**: optional (experimental), default: NONE. Amount to favor user history in creating recommendations that also have an item or item-set in the query. 1 is neutral, fractional is de-boosting, greater than 1 is boosting.
* **itemBias**: optional (experimental), default: NONE. Amount to favor item information in creating recommendations that have user or an item-set in the query. 1 is neutral, fractional is de-boosting, greater than 1 is boosting.
* **itemSetBias**: optional (experimental), default: NONE. Amount to favor item-set information in creating recommendations that have a user or item in the query. 1 is neutral, fractional is de-boosting, greater than 1 is boosting.

    **Note**: ***biases*** are often not the best way to mix recommendations based on user history and item or item-set  similarity. There is no way, when using a mix of examples in queries to control how many recommendations are based on each example (user, item, or item-set). Therefore it is suggested that several queries are made and results mixed as desired by the application. However there are special cases where the use of multiple examples might be beneficial.
* **expireDateName** optional, default: NONE. The name of the item property field that contains the date an item expires or is unavailable to recommend.
* **availableDateName** optional, default: NONE. The name of the item property field that contains the date the item is available to recommend. 
* **dateName** optional, default: NONE. The name of the item property field that contains a date or timestamp to be used in a `dateRange` query clause.
* **returnSelf**: optional, default: false. Boolean flagging the fact that the item example in the query is a valid result. The default is to never return the example item or one of an item-set in a query result, which is by far the typical case. Where items make be periodically recommended as with consumables (food?) is it usually better to mix these into recommendations based on an application algorithm rather than use the recommender to return them. For instance food items that are popular for a specific user might be added to recommendations or put in some special placement outside of recommender results.
* **recsModel** optional, default: "all", which means  collaborative filtering with popular items or other ranking method returned IF no other recommendations can be made. If only "backfill" is specified then only some backfill or ranking type like "popular" will be returned. If only "collabFiltering" then no backfill will be included when there are not enough recommended items.
* **rankings** optional, the default is to use only `"type": "popular"` counting all primary events. This parameter, when specified, is a list of ranking methods used to rank items as fill-in when not enough recommendations can be returned using the CCO algorithm. Popular items usually get the best results and so are the default. It is sometimes useful to be able to return any item, even if it does not have events (popular would not return these) so we allow random ranking as a method to return items. There may also be a user defined way to rank items so this is also supported.
     
  This parameter is a list of ranking methods that work in the order specified. For instance if popular is first and it cannot return enough items the next method in the list will be used&mdash;perhaps random. Random is always able to return all items defined so it should be last in the list. 
  
  When the `"type"` is **"popular", "trending", or "hot"** this set of parameters defines the calculation of the popularity model that ranks all items by their events in one of three different ways corresponding to: event counts (popular), change in event counts over time (trending), and change in trending over time (hot).
  
  When the `"type"` is **"random"** all items are ranked randomly regardless of any usage events. This is useful if some items have no events but you want to present them to users given no other method to recommend.
  
  When the `"type"` is **"userDefined"** the property defined in `"name"` is expected to rank any items that you wish to use as backfill. This may be useful, for instance, if you wish to show promoted items when no other method to recommend is possible. 
  
  In all cases the property value defined by `"name"` must be given a unique float value. For `"popular"`, `"trending"`, `"hot"`, and `"random"` the value is calculated by the UR. For `"userDefined"` the value is set using a `$set` event like any other property. See "Property Change Events" [here](ur_input.md).
  
 - **name** give the field a name in the model and defaults to those mentioned above in the JSON.
 - **type**  `"popular"`, `"trending"`, `"hot"` can be defined and use event counts per item one of these can be used with `"userDefined"` and/or `"random"`. `"popular"`, `"trending"`, `"hot"` use event counts that are just count, change in event counts, or change in "trending" values. 
	
	 **Note**: when using "hot" the algorithm divides the events into three periods and since events tend to be cyclical by day, 3 days will produce results mostly free of daily effects. Making this time period smaller may cause odd effects. Popular is not split and trending splits the events in two. So choose the duration accordingly. 
	
	 These each add a rank value to items in the model that is used if collaborative filtering recommendations cannot be made. Since they rank all items they also obey filters, boosts, and business rules as any CF recommendation would. For example setting rankings allows CF to be preferred, then "popular" then "random" falling back to the ranking in the order they are defined.
	- **indicatorNames** this is allowed only with one of the popularity types and is an array of indicator/Event names to use in calculating the popularity model, this defaults to the primary/conversion Event&mdash;the first in the `algorithm.indicators` list. 
	- **duration**  this is allowed only with one of the popularity types and is a duration like "3 days" (which is the default), which defines the time from now back to the last event to count in the popularity calculation.
* **numESWriteConnections**: optional, default = number of threads in entire Spark Cluster, which may overload Elasticsearch when writing the trained model to it. 

    If you see task failures, event if retries cause no Job failure, this will help remove the errors by throttling the write operation to ES. The other option is to add to / scale out your ES cluster because this will slow the Spark cluster down by reducing the number of tasks used to write to ES and so remove the errors. The rule of thumb for this setting is (numberOfNodesHostingPrimaries * bulkRequestQueueLength) * 0.75. In general this is (numberOfESCores * 50) * 0.75, where 50 comes from the Elasticsearch bulk queue default.
* **seed** optional, default: random Set this if you want repeatable downsampling for some offline tests. This can be ignored and shouldn't be set in production. 
