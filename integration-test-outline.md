# Harness Integration Test

This test is meant to exercise all of the Harness CLI functionality with SDKs for input and queries. See [Commands](https://github.com/actionml/harness/blob/develop/commands.md), [Java SDK](https://github.com/actionml/harness/blob/develop/java-sdk.md), and the [README](https://github.com/actionml/harness/blob/develop/README.md)

For now, while we have only one Template, we use only the Contextual Bandit

The test should perform the following tasks, checking for correct results as much as possible at each step.

Assume a configured setup of Harness for single server execution.

 - Start the server, this assumes setup, config, and paths are correct.
 
    ```bash
    harness start
    ```
    
    This should print correct status including which engines are started
    
 - Add an engines and data

    ```bash
    harness add -c cb-engine-1.json
    ```
    
    The cbengine.com file will contain the resourceId of the engine and other params needed to create the engine, including the engine factory to instantiate a particular `CBEngine`.
    
    This should report the params parsed correctly and the values in the file. It should correctly install the Engine on the resourceId in the json file
    
    ```bash
    harness add -c cb-engine-2.json
    ```
    
    This should do the same as the `add` above but report different params and resourceId.
    
    ```bash
    harness add -c cb-engine-3.json
    ```
    
    This will have a duplicate resouceId and report the error ignoring the `add` command
    
    ```bash
    run-java-client-1
    ```
    
    This will input the example json to the cb-engine-1 resourceId with no errors
    
    ```bash
    run-java-client-2
    ```
    
    This will input the example json to the cb-engine-2 resourceId with no errors
    
    ```bash
    harness status <resource-id-1> # reports various status info
    harness list # reports 2 engines installed
    ```

 - Remove engines and data

    ```bash    
    harness remove <resource-id-1> -f # removes the engine data and endpoint
    harness list # reports only 1 engine on resource-id-2
    run-java-client-1 # reports unable to connect
    harness status <resource-id-1> # report no such engine
    ```
    
    Verify data removal in MongoDB to make sure the dataset and model data for `cb-engine-1` have been deleted as well as the metadata. This will be the removal of a MongoDB database of the name = `<resource-id-1>` and the metadata database entry for `<resource-id-1>`
    
 - Update an Engine

    ```bash
    harness update <resource-id-2> -c cb-engine-4.json    
    ```    
    
    This should report that it is **unimplemented**. When implemented this may move the engine to a new resource-id, update the algorithm params, and prompt to delete data since it may make no sense with new engine params.
    
    ```bash
    harness remove <resource-id-2> -f # removes the engine data and endpoint
    harness list # reports no engines
    run-java-client-1 # reports unable to connect
    harness status <resource-id-1> # report no such engine
    harness status # reports all is well, no engines
    ```

    Verify data removal in MongoDB to make sure the dataset and model data for `cb-engine-2` have been deleted as well as the metadata. This will be the removal of a MongoDB database of the name = `<resource-id-2>` and the metadata database entry for `<resource-id-2>` 
    
    At this point the metadata database may exist in MongoDB but it should have no engines.
    
    ```bash
    harness stop # prints the same as the last status message
    ```

 - Verify Harness start/stop 
 
    ```bash
    harness start # reports status, no engines
    harness add -c cb-engine-1 # reports as expected
    harness status # reports one engine on resource-id-1
    harness stop # reports last status message
    harness start # reports last status message, one engine installed
    harness stop
    ```
    
    Verify there are no running processes that are related to harness.