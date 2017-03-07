package com.actionml.templates.cb

import com.actionml.core.storage.Store
import com.actionml.core.template.Dataset

/** Implements the persistence for the Contextual Bandit
  * There are 2 types of events for the CB 1) usage events and 2) property change events. The usage events
  * are stored as a potentially very large collection, the propety change events translate into changes to
  * mutable DB objects.
  *
  * See the discussion of Kappa Learning here: https://github.com/actionml/pio-kappa/blob/master/kappa-learning.md
  *
  * @param resourceId REST resource-id for input
  * @param store where they are stored
  */
class CBDataset(resourceId: String, store: Store) extends Dataset[CBEvent](resourceId, store) {
  // the resourceId is used as a db-id in Mongo, under which will be collections/tables for
  // useage events (which have a datetime index and a TTL) and mutable tables that reflect state of
  // input like groups, and even ML type models created by Vowpal Wabbit's Contextual Bandit algo. The later
  // do not have TTL and reflect the state of the last watermarked model update.

  var events = Seq[CBEvent]() // todo: this will go in a Store eventually

  // todo: we may want to use some operators overloading if this fits some Scala idiom well, but uses a Store so
  // may end up more complicated.

  // add one datum, possibley an CBEvent, to the beginning of the dataset
  def append(datum: CBEvent): Boolean = {
    events = events :+ datum
    true
  }
  // takes a collection of data to append to the Dataset
  def appendAll(data: Seq[CBEvent]): Seq[Boolean] = {
    events = events ++: data
    Seq[Boolean](true)
  }

}
