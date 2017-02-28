package com.actionml;

import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.actionml.entity.Event;
import com.actionml.entity.EventId;
import com.google.gson.JsonElement;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         04.02.17 17:50
 */
public class EventClient extends RestClient {

    public EventClient(String datasetId, String host, Integer port) {
        super(host, port, Uri.create("/datasets").addPathSegment(datasetId).addPathSegment("events"));
    }

    /**
     * Get exist Event
     *
     * @param eventId ID event
     * @return Event
     */
    CompletionStage<Event> getEvent(String eventId) {
        return this.get(eventId).thenApply(jsonElement -> toPojo(jsonElement, Event.class));
    }

    public CompletionStage<EventId> createEvent(Event event) {
        return this.create(event.toJsonString()).thenApply(this::toEventId);
    }

    public CompletionStage<List<Pair<Long, EventId>>> createEvents(List<Event> events) {
        return Source.from(events)
                .map(Event::toJsonString)
                .map(this::createPost)
                .zipWithIndex()
                .map(pair -> pair.copy(pair.first(), (Long) pair.second()))
                .via(this.poolClientFlow)
                .mapAsync(1, this::extractResponse)
                .mapAsync(1, this::extractJson)
                .map(this::toEventId)
                .map(param -> param)
                .runFold(new ArrayList<>(), (acc, eventId) -> {
                    acc.add(eventId);
                    return acc;
                }, this.materializer);
    }

    protected EventId toEventId(JsonElement jsonElement) {
        return toPojo(jsonElement, EventId.class);
    }

    protected Pair<Long, EventId> toEventId(Pair<Long, JsonElement> pair) {
        return Pair.create(pair.first(), toEventId(pair.second()));
    }

    private Event buildEvent(String id, DateTime eventTime) {
        return new Event().entityId(id).eventTime(eventTime);
    }

    /*******************************************************************************************************************
     *              User actions
     ******************************************************************************************************************/

    private Event buildUserEvent(String uid, DateTime eventTime) {
        return buildEvent(uid, eventTime).entityType("user");
    }

    private Event buildUserEvent(String uid, Map<String, Object> properties, DateTime eventTime) {
        return buildUserEvent(uid, eventTime).properties(properties);
    }

    /**
     * Sends a set user properties request. Implicitly creates the user if it's not already there.
     * Properties could be empty.
     *
     * @param uid        ID of the user
     * @param properties a map of all the properties to be associated with the user, could be empty
     * @param eventTime  timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> setUser(String uid, Map<String, Object> properties, DateTime eventTime) {
        Event event = buildUserEvent(uid, properties, eventTime).event("$set");
        return createEvent(event);
    }

    /**
     * Sets properties of a user. Same as {@link #setUser(String, Map, DateTime)}
     * except event time is not specified and recorded as the time when the function is called.
     */
    public CompletionStage<EventId> setUser(String uid, Map<String, Object> properties) {
        return setUser(uid, properties, new DateTime());
    }

    /**
     * Unsets properties of a user. The list must not be empty.
     *
     * @param uid        ID of the user
     * @param properties a list of all the properties to unset
     * @param eventTime  timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> unsetUser(String uid, List<String> properties, DateTime eventTime) throws IOException {
        if (properties.isEmpty()) {
            throw new IllegalStateException("property list cannot be empty");
        }
        // converts the list into a map (to empty string) before creating the event object
        Map<String, Object> propertiesMap = properties.stream().collect(Collectors.toMap(o -> o, s -> ""));
        Event event = buildUserEvent(uid, propertiesMap, eventTime).event("$unset");
        return createEvent(event);
    }

    /**
     * Unsets properties of a user. Same as {@link #unsetUser(String, List, DateTime)
     * unsetUser(String, List&lt;String&gt;, DateTime)}
     * except event time is not specified and recorded as the time when the function is called.
     */
    public CompletionStage<EventId> unsetUser(String uid, List<String> properties) throws IOException {
        return unsetUser(uid, properties, new DateTime());
    }

    /**
     * Deletes a user.
     *
     * @param uid       ID of the user
     * @param eventTime timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> deleteUser(String uid, DateTime eventTime) {
        Event event = buildUserEvent(uid, eventTime).event("$delete");
        return createEvent(event);
    }

    /**
     * Deletes a user. Event time is recorded as the time when the function is called.
     *
     * @param uid ID of the user
     * @return ID of this event
     */
    public CompletionStage<EventId> deleteUser(String uid) {
        return deleteUser(uid, new DateTime());
    }

    /*******************************************************************************************************************
     *              Item actions
     ******************************************************************************************************************/

    private Event buildItemEvent(String iid, DateTime eventTime) {
        return buildEvent(iid, eventTime).entityType("item");
    }

    private Event buildItemEvent(String iid, Map<String, Object> properties, DateTime eventTime) {
        return buildItemEvent(iid, eventTime).properties(properties);
    }

    /**
     * Sets properties of a item. Implicitly creates the item if it's not already there.
     * Properties could be empty.
     *
     * @param iid        ID of the item
     * @param properties a map of all the properties to be associated with the item, could be empty
     * @param eventTime  timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> setItem(String iid, Map<String, Object> properties, DateTime eventTime) {
        Event event = buildItemEvent(iid, properties, eventTime).event("$set");
        return createEvent(event);
    }

    /**
     * Sets properties of a item. Same as {@link #setItem(String, Map, DateTime)
     * setItem(String, Map&lt;String, Object&gt;, DateTime)}
     * except event time is not specified and recorded as the time when the function is called.
     */
    public CompletionStage<EventId> setItem(String iid, Map<String, Object> properties) {
        return setItem(iid, properties, new DateTime());
    }

    /**
     * Unsets properties of a item. The list must not be empty.
     *
     * @param iid        ID of the item
     * @param properties a list of all the properties to unset
     * @param eventTime  timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> unsetItem(String iid, List<String> properties, DateTime eventTime) throws IOException {
        if (properties.isEmpty()) {
            throw new IllegalStateException("property list cannot be empty");
        }
        // converts the list into a map (to empty string) before creating the event object
        Map<String, Object> propertiesMap = properties.stream().collect(Collectors.toMap(o -> o, s -> ""));
        Event event = buildItemEvent(iid, propertiesMap, eventTime).event("$unset");
        return createEvent(event);
    }

    /**
     * Unsets properties of a item. Same as {@link #unsetItem(String, List, DateTime)
     * unsetItem(String, List&lt;String&gt;, DateTime)}
     * except event time is not specified and recorded as the time when the function is called.
     */
    public CompletionStage<EventId> unsetItem(String iid, List<String> properties) throws IOException {
        return unsetItem(iid, properties, new DateTime());
    }

    /**
     * Deletes a item.
     *
     * @param iid       ID of the item
     * @param eventTime timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> deleteItem(String iid, DateTime eventTime) {
        Event event = buildItemEvent(iid, eventTime).event("$delete");
        return createEvent(event);
    }

    /**
     * Deletes a item. Event time is recorded as the time when the function is called.
     *
     * @param iid ID of the item
     * @return ID of this event
     */
    public CompletionStage<EventId> deleteItem(String iid) {
        return deleteItem(iid, new DateTime());
    }

    /*******************************************************************************************************************
     *              User to Item actions
     ******************************************************************************************************************/

    /**
     * Records a user-action-on-item event.
     *
     * @param action     name of the action performed
     * @param uid        ID of the user
     * @param iid        ID of the item
     * @param properties a map of properties associated with this action
     * @param eventTime  timestamp of the event
     * @return ID of this event
     */
    public CompletionStage<EventId> userActionItem(String action, String uid, String iid, Map<String, Object> properties, DateTime eventTime) {
        Event event = buildUserEvent(uid, properties, eventTime).event(action).targetEntityType("item").targetEntityId(iid);
        return createEvent(event);
    }

    public CompletionStage<EventId> userActionItem(String action, String uid, String iid, Map<String, Object> properties) {
        return userActionItem(action, uid, iid, properties, new DateTime());
    }

}
