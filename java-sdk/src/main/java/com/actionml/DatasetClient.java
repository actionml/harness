package com.actionml;

import akka.http.javadsl.model.Uri;
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

    public CompletionStage<DatasetId> createDataset() {
        return this.create().thenApply(this::toDatasetId);
    }

    public CompletionStage<DatasetId> createDataset(String datasetId) {
        return this.create(datasetId, "{}").thenApply(this::toDatasetId);
    }

    public CompletionStage<DatasetId> createDataset(DatasetId datasetId) {
        return createDataset(datasetId.getDatasetId());
    }

    public CompletionStage<DatasetId> getDataset(String datasetId) {
        return this.get(datasetId).thenApply(this::toDatasetId);
    }

    public CompletionStage<DatasetId> getDataset(DatasetId datasetId) {
        return getDataset(datasetId.getDatasetId());
    }

    public CompletionStage<DatasetId> deleteDataset(String datasetId) {
        return this.delete(datasetId).thenApply(this::toDatasetId);
    }

    public CompletionStage<DatasetId> deleteDataset(DatasetId datasetId) {
        return deleteDataset(datasetId.getDatasetId());
    }

    protected DatasetId toDatasetId(JsonElement jsonElement) {
        return toPojo(jsonElement, DatasetId.class);
    }
}
