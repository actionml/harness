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

package com.actionml;

import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import com.actionml.entity.DatasetId;
import com.google.gson.JsonElement;

import java.util.concurrent.CompletionStage;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         18.02.17 17:47
 */
public class DatasetClient extends RestClient {

    DatasetClient(String host, Integer port) {
        super(host, port, Uri.create("/datasets"));
    }

    public CompletionStage<Pair<Integer, String>> createDataset() {
        return this.create();
    }

    public CompletionStage<Pair<Integer, String>> createDataset(String datasetId) {
        return this.create(datasetId, "{}");
    }

    public CompletionStage<Pair<Integer, String>> createDataset(DatasetId datasetId) {
        return createDataset(datasetId.getDatasetId());
    }

    public CompletionStage<Pair<Integer, String>> getDataset(String datasetId) {
        return this.get(datasetId);
    }

    public CompletionStage<Pair<Integer, String>> getDataset(DatasetId datasetId) {
        return getDataset(datasetId.getDatasetId());
    }

    public CompletionStage<Pair<Integer, String>> deleteDataset(String datasetId) {
        return this.delete(datasetId);
    }

    public CompletionStage<Pair<Integer, String>> deleteDataset(DatasetId datasetId) {
        return deleteDataset(datasetId.getDatasetId());
    }

}
