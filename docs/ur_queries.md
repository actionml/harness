# Queries

The Universal Recommender has a reasonable set of defaults so queries can be very simple or, when the need arises, very flexible. There are two parts of all UR Queries:

 1. **The Subject**: This is a user-id, item-id, or list of item-ids. Recommendations consist of the best item matches to the subject. For users this means the items that have had interactions from the most similar users. For items is means other items that have the most similar interactions.
 2. **Business Rules**: These consist of restrictions on the items that can be recommended and define matches to item properties that will allow. boost, or exclude them as recommendations. If an item cannot be recommended the Business Rules will never cause them to be returned for a Query.

## The Simplest Query

```
{}
```

This has no user, items, or item-set so it can do nothing but return popular items. For every model there is one "popularity" method chosen, that ranks all items that have interactions. This translates to all items that are the target of Events/Indicators. Configuration for the model is supplied in the UR engines JSON `algorithm` parameters and if not given, just counts the number of primary Indicator Events per item for all recorded time. See [Configuration](ur_advanced_config.md) for other tuning of the "popularity" ranking.

## Simple Personalized User-based Query

```
{
  "user": "John Doe"
}
```
	    	
This gets historical data for "John Doe" and uses this to return the best personalized recommendations. John has left a record of indicators by interacting with a site or app in a way that triggered Events. So depending on what was recorded we would expect maybe purchases, search terms, category-prefs to be in John's history (Harness records this in realtime). So only a moment ago, if John searched, these terms might be used in making recommendations.

## Item-based Query

```
{
  "item": "iPad"
}
```
	    	
This query is the kind you see at the bottom of a product page in Amazon. It shows "other people who bought 'iPad' also liked these". It returns items that have seen similar user behavior. This means it is non-personalized (after all there is no user in the query) but finds items which on average are similar to the item in the query. Similarity here is only based on the indicators of other users, not item properties. This type of recommendation is quite useful as the Amazon use case shows, and can be used if you know nothing about the user. It may be useful to think of these as item-details page recommendations.


## Simple Item-set Query (Wishlist)

This query applies to a wide variety of lists, like watchlists, favorites, shopping carts, wishlists, etc. It returns Similar iItems to the items in the list.

```
{
  "itemSet": ["item-1", "item-5", "item-300", "item-2", ...]   
}
```

To get the missing items you will need to train a separate model on item-sets (not individual user behavior). However even if your model is made from user behavior this query has a place for lists that do not naturally have missing pieces.

## Complimentary Items (Shopping-cart)

The query will be exactly the same as an item-set but to get "Complimentary Items" based on things that go together, you need to create a model from some grouping of items like things bought together in a shopping cart or items viewed in a session, etc. Doing this will turn the query from "find items similar to all these" into "find the missing items from this list" this is an important distinction. For example similar items may all look the same, complimentary items may look quite different. If John has a Galaxy S10 and  Galaxy Case in this shopping cart, would it be better to recommend "USB-C cord" or "Galaxy S9"? Past experience and research shows that Complimentary Items get better results than Similar Items in many cases. 

This topic is more advanced and takes us into methods for generating different model types so see [Use Cases](ur_use_cases.md)

# Business Rules

Business Rules overrule the ranking of items recommended. They put restrictions on what a query can return or reorder them. These restrictions are based on item properties. Therefore add item properties (using `$set` Events) to support the Rules you need. For example `$set` the `in-stock` property to `true` for items that can be converted on, if you later want to restrict recommendations to in-stock items.

Since Business Rules restrict items that can be recommended they will *theoretically* result in fewer conversions overall. However this may not be true in practice. For instance requiring an item be in-stock will likely cause more conversions. However be careful of using business rules just because they *seem correct*. If intuition is wrong they can reduce performance.

## Rule Types

 - **Include**: Only include recommended items that match the Rule. Think if these as inclusion filters.
 - **Exclude**: Exclude all items that match this Rule. Think of these as exclusion filters.
 - **Boost**: Multiply the recommendation score by the `"bias"`. This does not guarantee inclusion or exclusion, and is better to use if a hard inclusion or exclusion is not required. Boost Rules will never result in empty recommendation, of their own accord, while the filter types Rule may yield empty recommendations.

The `"name"` and `"value"` of the Rule definition below are fairly clear. The `"bias"` however picks which of the above types are executed:

 - **bias = -1**: Include recommended items that match the rest of the Rule 
 - **bias = 0**: Exclude recommended items that match the rest of the Rule 
 - **bias > 0**: Boost recommended items that match the rest of the Rule by the bias value. This will cause matching recommendations to be moved upward in ranking of returned results.

The `"name"` identifies the property name to match.
The `"values"` provide a list of values that the properties of the recommended items are matched against. If the Rule is inclusion then at least one of the values must match the property values. if the Rule is exclusion then no value is allowed to match the recommended items. If the Rule is a boost them effectively the score of the recommendation is multiplied by the bias, taking into account how many values match the item.

The way to think about Boost Rules is that they favor recommended items that match more of the values and so raise them in the ranking. If the score of a non-matching recommended item is still higher than the boosted score it will be recommended higher, but it is possible to increase the bias amount to almost guarantee items matching the Rules will come out on top. 

## Personalized with Rules

```
{
  "user": "John Doe",
  "rules": [
    {
      "name": "categories"
      "values": ["series", "mini-series"],
      "bias": -1 // filter out all except 'series' or 'mini-series'
    },{
      "name": "genre",
      "values": ["sci-fi", "detective"]
      "bias": 1.02 // boost/favor recommendations with the 'genre' = 'sci-fi' or 'detective'
    }
  ]
}
```

This returns items based on user "John Doe" history including only the categories mentioned and boosted a little bit to favor more genres specified. (**Note**: the values for properties have been attached to items with `"$set"` events) The "bias" is used to indicate a filter or a boost.  As always the blacklist, backfill, and other settings affecting results use the defaults in Engine's config.

## Date Ranges in Queries

When the a date is stored in the items properties.

```
{
  "user": "xyz", 
  "fields": [
    {
      "name": "categories"
      "values": ["series", "mini-series"],
      "bias": -1 }// filter out all except 'series' or 'mini-series'
    },{
      "name": "genre",
      "values": ["sci-fi", "detective"]
      "bias": 1.02 // boost/favor recommendations with the 'genre' = 'sci-fi' or 'detective'
    }
  ],
  "dateRange": {
    "name": "availabledate",
    "before": "2015-08-15T11:28:45.114-07:00",
    "after": "2015-08-20T11:28:45.114-07:00       
  }
}
```
	

Items are assumed to have a field of the same `"name"` that has a date associated with it using a `"$set"` event. The query will return only those recommendations where the date field is in `"dateRange"`. Either date bound can be omitted for a one-sided range. The range applies to all returned recommendations, even those for popular items. 	

## Queries using Available and Expire Dates

When setting an available date and expire date on Items you will set the name of the fields to be used in the Engine's config `expireDateName` and `availableDateName` params, the current date of the Harness server will be used as a filter. The UR will check that the current date is before the expire date, and after the available date. If the above fields are defined in the Engine's config a date must accompany any query since all items are assumed to have this property. When setting these values for item properties both must be specified so if a one-sided query is desired set the available date to some time in the past and/or the expire date to sometime far in the future, this guarantees that the item will not be filtered out from one or the other limit. If the available and expire fields are named in the Engine's config then the current Harness server date will be used. 

**Note:** a somewhat hidden effect of this is that if these fields are specified in the Engine's config the filter will apply to **every query made**. So diabling for specific items must be done by manipulating their available/expire dates.

```
{
  "user": "xyz", 
  "fields": [
    {
      "name": "categories"
      "values": ["series", "mini-series"],
      "bias": -1 }// filter out all except 'series' or 'mini-series'
    },{
      "name": "genre",
      "values": ["sci-fi", "detective"]
      "bias": 1.02	    }
  ],
}
```

**Note**: No values are supplied in the query is the date names are in the Engine's Config so this **looks** the same as a query with no available/expire. Check the Engine's config to know for sure if this kind of filter is being used.

## Personalized Queries with Similar Items

```
{
  "user": "John Doe", 
  "userBias": 2, // favor personalized recommendations
  "item": "Mr Robot", // fallback to contextual recommendations
  "rules": [
    {
      "name": "categories"
      "values": ["series", "mini-series"],
      "bias": -1 }// filter out all except 'series' or 'mini-series'
    },{
      "name": "genre",
      "values": ["sci-fi", "detective"]
      "bias": 1.02 // boost/favor recommendations with the 'genre' = 'sci-fi' or 'detective'
    }
  ]
}
```

This returns items based on "John Doe"s  history or similar to item "Mr Robot" but favoring user history-based recommendations. These are filtered by categories and boosted to favor genre specific items. 

**Note**:This query should be considered **experimental**. Mixing user history with item similarity is possible but may have unexpected results. It is also possible to make 2 queries, one user-based and one item-based and mix the results and this may be better. 

# Query Parameter Specification

Query fields determine what data is used to match when returning recommendations.

```
{
  "user": "John Doe", 
  "userBias": -maxFloat..maxFloat,
  "item": "iPad", 
  "itemBias": -maxFloat..maxFloat,    
  "itemSet": ["cd53454543513", "lg1", "vf23423432", "af87634"], 
  "itemSetBias": -maxFloat..maxFloat,  
  "from": 0,
  "num": 4,
  "rules": [
    {
      "name": "fieldname"
      "values": ["fieldValue1", ...],
      "bias": -maxFloat..maxFloat 
    },...
  ]
  "dateRange": {
    "name": "dateFieldname",
    "before": "2015-09-15T11:28:45.114-07:00",
    "after": "2015-08-15T11:28:45.114-07:00"
  },
  "blacklistItems": ["itemId1", "itemId2", ...]
  "returnSelf": true | false,
}
```

* **user**: optional, contains a unique id for the user. This may be a user not in the **training**: data, so a new or anonymous user who has an anonymous id. All user history captured in near realtime can be used to influence recommendations, there is no need to retrain to enable this.
* **userBias**: optional (use with great care), the amount to favor the user's history in making recommendations. The user may be anonymous as long as the id is unique from any authenticated user. This tells the recommender to return recommendations based on the user's event history. Used for personalized recommendations. Overrides setting in Engine's config.
* **item**: optional, contains the unique item identifier
* **itemBias**: optional (use with great care), the amount to favor similar items in making recommendations. This tells the recommender to return items similar to this the item specified. Use for "people who liked this also liked these". Overrides any bias in Engine's config
* **itemSet**: optional, contains a list of unique item identifiers
* **itemSetBias**: optional (use with great care), the amount to favor itemSets in making recommendations. Mixing itemSet queries with user and item queries is not recommended and it is difficult to predict what it will return in the final mixed results.
* **rules**: optional, array of fields values and biases to use in this query. 
	* **name** field name for metadata stored in the EventStore with $set and $unset events.
	* **values** an array on one or more values to use in this query. The values will be looked for in the field name. 
	* **bias** will either boost the importance of this part of the query or use it as a filter. Positive biases are boosts any negative number will filter out any results that do not contain the values in the field name. See [Biases]().
* **from**: optional rank/position to start returning recommendations from. Used in pagination. The rank/position is 0 based, 0 being the highest rank/position. Default: 0, since 0 is the first or top recommendation. Unless you are paginating skip this param.
* **num**: optional max number of recommendations to return. There is no guarantee that this number will be returned for every query. Adding backfill in the Engine's config will make it much more likely to return this number of recommendations.
* **blacklistItems**: optional. Unlike the Engine's config, which specifies event types this part of the query specifies individual items to remove from returned recommendations. It can be used to remove duplicates when items are already shown in a specific context. This is called anti-flood in recommender use.
* **dateRange** optional, default is not range filter. One of the bound can be omitted but not both. Values for the `before` and `after` are strings in ISO 8601 format. Overrides the **currentDate** if both are in the query.
* **returnSelf**: optional boolean asking to include the item that was part of the query (if there was one) as part of the results. Defaults to false.
 
Defaults are either noted or taken from algorithm values, which themselves may have defaults. This allows very simple queries for the simple, most used cases.
 
The query returns popular, personalized recommendations, similar items, similar to an item-set, or a mix of all these. The query itself determines this by supplying item, user, item-set, or any mix.

