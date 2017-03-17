/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.entity;

import com.actionml.DateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.joda.time.DateTime;

/**
 * Created by semen on 01.03.17.
 */

// Todo: Semen, the query cannot be this specific, it should probably send a json string for the
// server to parse since every template will have completely different query, Did you check the PIO Java SDK for
// Query and QueryResults handling?
public class Query {

    private String user;
    private String groupId;


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

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Query user(String user) {
        this.user = user;
        return this;
    }

    public Query groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }
}
