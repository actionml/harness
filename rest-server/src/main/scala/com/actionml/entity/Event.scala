package com.actionml.entity

import io.circe._

/**
  *
  * <a href="http://predictionio.incubator.apache.org/datacollection/eventapi/">http://predictionio.incubator.apache.org/datacollection/eventapi/</a>
  *
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 28.01.17 12:14
  */
case class Event(
  // Name of the event.
  // (Examples: "sign-up", "rate", "view", "buy").
  event: String,

  // The entity type. It is the namespace of the entityId
  // and analogous to the table name of a relational database.
  // The entityId must be unique within same entityType.
  entityType: String,

  // The entity ID. entityType-entityId becomes the unique identifier of the entity.
  // For example, you may have entityType named user, and different entity IDs,
  // say 1 and 2. In this case, user-1 and user-2 uniquely identifies entities.
  entityId: String,

  // The target entity type.
  targetEntityType: Option[String] = None,

  // The target entity ID.
  targetEntityId: Option[String] = None,

  // See Note About Properties below
  properties: Option[Map[String, Json]] = None,

  // The time of the event.
  // Must be in ISO 8601 format (e.g.2004-12-13T21:39:45.618Z, or 2014-09-09T16:17:42.937-08:00).
  eventTime: Option[String] = None
)
