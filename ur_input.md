# UR Input

The Universal Recommender input is formed of what are called "indicators" because you expect them to indicate something about user's preferences. They provide some bit of information about user behavior, preference, or attribute. Indicators come in two types that have very different uses:

 1. **User indicators** These are always tied to users and often indicate some user action, though they are not limited to actual events and can be extended to user profile properties and usage context. A purchase, video view, article read, search term, share, location, and many other things can be used as indicators.  
    
    - **Primary indicators**: This is required by the UR algorithm. It is a record of the type of thing you want to recommend&mdash;buy, play, watch, read, etc. This should include the items from which you will recommend.
    
    - **Secondary events**: These encode anything else we know about the user that we think may be an indicator of taste&mdash;category or genre preference, location, pageviews, location, user profile data, tag preference, likes, follows, shares, even search terms.
    
 2. **Item property changes** are always tied to items and are used to set, change, or unset properties of items. User properties are not supported with property change events only through  preference indicators/usage events.
 
The UR have many indicators as input. These should be seen as a primary indicator, which is a very clear indication of a user preference and secondary indicators that we think may tell us something about user "taste" in some way. The Universal Recommender is built on a distributed Correlated Cross-Occurrence (CCO) Engine, which basically means that it will test every secondary indicator to make sure that it actually correlates to the primary one and those that do not correlate will have little or no effect on recommendations. See ActionML's [Analysis Tools](/docs/ur_advanced_tuning/#mapk) for methods to test event predictiveness.

## Preference Indicators aka Usage Events

Events in Harness are used to encode indicators and item property changes. They match Apache PredictionIO so that exporting from PIO will produce files that can be imported into Harness + UR.

Events are JSON objects that encode data sent to the REST input API. The UR interprets them as either Indicators or Item Property Change Events.

An ECommerce Primary Indicator might look like this:

```
{
   "event" : "purchase",
   "entityType" : "user",
   "entityId" : "John Doe",
   "targetEntityType" : "item",
   "targetEntityId" : "iPad",
   "properties" : {},
   "eventTime" : "2015-10-05T21:02:49.228Z"
}
```

Rules for Indicators are:

 - **event**: the value must be one the `"name"`s in the `"indicators"` array from the UR engine's JSON config.
 - **entityType**: This is **always** "user", do not use any other type for indicators. 
 - **entityId**: This is whatever string you use to identify a user.
 - **targetEntityType**: This is **always** "item", do not use any other type for indicators.
 - **targetEntityId**: The id for items that correspond to the indicator name. May be a product-id, category-id, a tag-id, search term, location,  anything that the event represents.
 - **properties**: always empty and can be omitted.
 - **eventTime**: the ISO8601 formatted string for the time the event occurred

This is what a "purchase" event looks like. Note that a usage event **always** is from a user and has a user id. Also the "targetEntityType" is **always** "item". The actual target entity is implied by the event's `"event"` attribute. So to create a `"category-preference"` event you would send something like this:

```
{
   "event" : "category-preference",
   "entityType" : "user",
   "entityId" : "John Doe",
   "targetEntityType" : "item",
   "targetEntityId" : "electronics",
   "properties" : {},
   "eventTime" : "2015-10-05T21:02:49.228Z"
}
```
   
This event might be sent when the user clicked on the "electronics" category or perhaps purchased an item that was in the "electronics" category. Note again that the "targetEntityType" is always "item".

## Property Change Events

Certain Event Names are reserved for special purposes that apply to any Engine attached to Harness. Theses apply to the UR to create, update, or delete Item Properties. They will always be targeted at item-ids.

To attach properties to items use a `"$set"` event like this:

```
{
   "event" : "$set",
   "entityType" : "item",
   "entityId" : "ipad",
   "properties" : {
      "category": ["electronics", "mobile-phones"],
      "expireDate": "2016-10-05T21:02:49.228Z"
   },
   "eventTime" : "2015-10-05T21:02:49.228Z"
}
```
   
```
{
   "event":"$set",
   "entityType":"item",
   "entityId":"Mr Robot",
   "properties": {
      "content-type":["tv show"],
      "genres":["suspense","sci-fi", "drama"],
      "actor":["Rami Malek", "Christian Slater"],
      "keywords":["hacker"],
      "first_air_at":["2015"]
   }
   "eventTime" : "2016-10-05T21:02:49.228Z"
}
```


Unless a property has a special meaning specified in engine instances JSON conifg, like date values, the property is assumed to be an array of strings, which act as categorical tags. 

## Brief Intro to "Rules"

You can add things like "premium" to the "tier" property then later if the user is a subscriber you can set a filter that allows recommendations from `"tier": ["free", "premium"]` where a non subscriber might only get recommendations for `"tier": ["free"]`. These are passed in to the query using the `"rules"` parameter (see Queries).

Using properties is how boost and filter rules are applied to recommended items. It may seem odd to treat a category as a filter **and** as a secondary event (category-preference) but the two pieces of data are used in quite different ways. As properties they bias the recommendations, when they are events they add to user data that returns recommendations. In other words as properties they work with boost and filter business rules as secondary usage events they show something about user taste to make recommendations better.
