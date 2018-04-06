# The Contextual Bandit

This Harness Template is based on Vowpal Wabbit's - CSOAA algorithm.  

The CB gets data and is trained in approximately realtime making queries available immediately. With every new input the recommender model is updated automatically and without interruption the fresh predictions begin with the next query.

[For some discussion of the underlying algorithm and citations](cb_algorithm.md)

# Events

Event come in via the Harness REST API for POSTing events in JSON form. Insert events in JSON form like so: 

```
curl -i -X POST http://localhost:9090/engines/<engine-id>/events \
-H "Content-Type: application/json" \
-d $JSONHERE
```

This assumes the server is listening on localhost:9090, the default, the Contextual Bandit Engine is running on `/engines/<resourse-id>`, and there is no SSL or Authentication setup.

# Data Input for the Contextual Bandit

The CB uses only one event type. Page View / Conversion events are triggered by the action of displaying a page to a user and sending an event that records their click or non-click. This event also contains the id of the test grouping which groups together a set of variants to be considered]. 

# User Attributes

The user attributes (context) can consist of any number of features, which do not need to be specified in advance. However, for the context to have an effect, use the same context features whenever possible within a single test grouping. 

The important information is always of the form (user-id, page-variant-id, test-group-id, conversion, context). So a simple example with only two users, two page-variants, two page views and a single context feature [gender] could look like the following: (userA, variantA, test-groupA, converted, male) and (userB, variantB, test-groupB, notConverted, female). These would be sent as 2 different events, one when the user context/profile is created and the other when the see a test grouping and respond.

The user attributes (context/profile) must be set independently of usage events. These must be specified at least once, in order for user context to be used, but they may also be updated later.

```
{
  "event" : "$set",
  "entityType" : "user"
  "entityId" : "amerritt",
  "eventTime" : "2014-11-02T09:39:45.618-08:00", // Optional
  "properties" : {
    "gender": ["male"],
    "country" : ["Canada"],
    "anotherContextFeature": ["A", "B"]
  }
}
```

# Initialize test group

The system needs to  know what page-variants can be recommended for each test group in advance, so that it can sample from them to make recommendations. That means the test group must be initialized before the system receives usage events for the group or is queried.

```
{
  "event" : "$set",
  "entityType" : "testGroup"
  "entityId" : "A",
  "properties" : {
    "testPeriodStart": "2016-01-02T09:39:45.618-08:00",
    "testPeriodEnd": "2016-02-02T09:39:45.618-08:00", 
    "pageVariants" : ["variantA", "variantB","variantC", "variantD", "variantE"]
  },
  "eventTime" : "2016-01-02T09:39:45.618-08:00" //Optional
}
```

Note that once the testPeriod has elapsed, that the recommender will then be deterministic (best prediction always chosen, no randomness). You can continue to use the recommender after this period, but the test should be considered complete. To continue to learn new user behavior in situations where few user context/profile attributes are available it is recommended that the test never end so new users will be learned and receive their best recommendations.

You can also set a test group again, to re-run the same test. You can update the testPeriodStart and testPeriodEnd properties by using another $set just like above. Please be sure however to not update the pageVariants or you may see undesirable results. In case you wish to create another test with different pageVariants, define a brand new testGroup.

# Usage Events/Indicators

All usage events can be thought of as (user-id, page-variant-id, test-group-id, conversion, context) but they must be encoded into "events". 

Using the SDK of your choice define an event of the form:

```
{
   "event" : "page-view-conversion",
   "entityType" : "user",
   "entityId" : "amerritt",
   "targetEntityType" : "variant",
   "targetEntityId" : "variantA",
   "properties" : {
      "testGroupId" : "A",
      "converted" : true
    }
}
```

 - **event**: this must be named "page-view-conversion"
 - **entityType**: this must be "user"
 - **entityId**: this is a user-id
 - **targetEntityType**: this must be "variant"
 - **targetEntityId**: this should be a variant-id
 - **properties**: define
  - **testGroupId**: id of a set of variants
  - **converted**: true or false

# Contextual Bandit Query API

On the `POST /engines/<engine-id>/queries` endpoint the Engine will respond to the JSON query

The PVR make personalized queries. A simple example using curl for personalized recommendations is:

```
curl -H "Content-Type: application/json" -d '
{
  "user": "psmith", 
  "testGroupId": "testGroupA"
}' http://localhost:9090/engines/<engine-id>/queries
```

This will get recommendations for user: "psmith", These will be returned as a JSON object looking like this:

```
{
  "Variant": "variantA",
  "testGroupId": "testGroupA"
}
```

Note that you can also request a page variant for a new or anonymous user, in which case the recommendations will be based purely on the context features (e.g. gender, country, etc.). If there is no information about the user the most frequently converted variant will be returned

# Configuration of the Contextual Bandit

The CB has a configuration file defined below. This defines parameters for for the Engine itself and for the VW compute engine which updates the model and responds to queries. The parameters effect how fast the CB will converge on the best choice for a user. Most parameters are defaulted to the ones below and unless you want to use something else, need not be specified.

```
{
  "engineId": "test_resource",
  "engineFactory": "com.actionml.templates.cb.CBEngine",
  "modelContainer": "!!put the path to a directory for models here!!",
  "algorithm":{
    "maxIter": 100,
    "regParam": 0.0,
    "stepSize": 0.1,
    "bitPrecision": 24,
    "namespace": "name",
    "maxClasses": 3
  }
}
```

 - **engineId**: used for the resource-id in the REST API. Can be any URI fragment.
 - **engineFactory**: constructs the Engine and reads the parameters, must be as shown.
 - **modelContainer**: this must point to a directory on the server machine and will be used by VW to keep the live model in a file named the same as the `engineId` above.
 - **sharedDBName**: The name of a db shared between all engine instances that as configured to use it. Each CBEngine will put user profile information here with the goal of using any shared user information in all algorithms that have access. It is assumed that if 2 or more clients use this shared DB that user ids are globally unique (only the same user will have the same id between clients). If is further assumed that the profile information shared is something that gives and indication for what variant a user might prefer. For example "name" would not be a good shared user profile attribute, but "location" might be. 
 - **algorithm**: params known only by the algorithm, which is a part of the Template definition.
  - **maxIter**: max iterations to calculate classification, best left at 100 so pathological cases won't take forever to complete.
  - **regParam**: regularization parameter, see VW docs.
  - **stepSize**: steps for iterations, se VW docs
  - **bitPrecision**: how many bits used to define a fixed point value internally.
  - **nameSpace**: **MUST** be unique for every resource-id. **WARNING**: this will be **deprecated** and calculated automatically.
  - **maxClasses**: max number of variants.

# Training

Harness was designed for streaming data sources and in the case of the CB will train for each incoming event incrementally so all you need to do is send events and make queries.

# Notes

This template requires Vowpal Wabbit. The included dependency in the build.sbt has been tested on Ubuntu 16.04. The dependency is only the Java API for VW and still requires that VW is built and installed from source as described in the [VW GitHub repo](https://github.com/JohnLangford/vowpal_wabbit). Follow the special Ubuntu instructions in [install.md](install.md).

