# Harness Integration Test

This test is meant to exercise as much of the Harness CLI, SDK, and Engines functionality as possible. See [Commands](commands.md), [Java SDK](java-sdk.md), and the [README](README.md). The [Python SDK](python-sdk/README.md) is exercised because it is used by the SDK but not for Events and Queries.

We create separate integration tests for every usable Engine, which as of Harness 0.1.1 means The Contextual Bandit and Navigation Hinting. As more Harness features of Engines are created the main integration test should be added to.

The test should perform the following tasks, checking for correct results as much as possible at each step.

Assume a configured setup of Harness for single server execution communicating with localhost. SSL can be setup, independently, the test should run out of the box with the default non-SSL config.

 - Start the server, this assumes setup, config, and paths are correct.
 
    ```bash
    harness start
    ```
    
    This should print correct status showing that connections to the localhost port are "OK"
    
 - Run the test scripts

    ```bash
    cd java-sdk
    ./integration-test
    ```
    
    This will add, and delete engines, send input and maake queries for every engine. It will even start/stop Harness to make sure persistence is working correctly. Once the test complete a diff of expected results and actual results will be printed for all Engine tests. If there are no differences (other than version numbers and timing) the tests pass.
    
You may now stop harness or delete the unneeded Engines to clear unwanted data.    

    