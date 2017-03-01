package com.actionml.entity;

import com.actionml.DateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.joda.time.DateTime;

/**
 * Created by semen on 01.03.17.
 */
public class QueryResult {

    private String variant;
    private String groupId;

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String toJsonString() {
        return toString();
    }

    @Override
    public String toString() {
        // handle DateTime separately
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeAdapter());
        Gson gson = gsonBuilder.create();
        return gson.toJson(this); // works when there are no generic types
    }
}
