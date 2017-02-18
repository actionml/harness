package com.actionml;

import akka.http.javadsl.model.Uri;
import com.actionml.entity.DatasetId;

import java.util.concurrent.CompletionStage;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 *         18.02.17 17:47
 */
public class DatasetClient extends BaseClient {

    private Uri uri = Uri.create("/datasets");

    DatasetClient(String host, Integer port) {
        super(host, port);
    }

    public CompletionStage<DatasetId> createDataset() {
        return this.post(uri, "{}")
                .thenApply(jsonElement -> toPojo(jsonElement, DatasetId.class));
    }

    public CompletionStage<DatasetId> createDataset(String datasetId) {
        return this.post(uri, datasetId, "{}")
                .thenApply(jsonElement -> toPojo(jsonElement, DatasetId.class));
    }

    public CompletionStage<DatasetId> createDataset(DatasetId datasetId) {
        return createDataset(datasetId.getDatasetId());
    }

    public CompletionStage<DatasetId> getDataset(String datasetId) {
        return this.get(uri, datasetId)
                .thenApply(jsonElement -> toPojo(jsonElement, DatasetId.class));
    }

    public CompletionStage<DatasetId> getDataset(DatasetId datasetId) {
        return getDataset(datasetId.getDatasetId());
    }

    public CompletionStage<DatasetId> deleteDataset(String datasetId) {
        return this.delete(uri, datasetId)
                .thenApply(jsonElement -> toPojo(jsonElement, DatasetId.class));
    }

    public CompletionStage<DatasetId> deleteDataset(DatasetId datasetId) {
        return deleteDataset(datasetId.getDatasetId());
    }
}
