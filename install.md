# Installing Harness Rest Server and Java SDK Examples

This guide allows you to install the server and Java SDK examples as well as set them up for debugging with IntelliJ. Much of this is targeted at macOS (BSD based).

To install on Ubuntu 14.04, which requires several extra steps&mdash;[see this page](https://github.com/actionml/harness/blob/develop/rest-server/install_ubuntu_14.md)

**Prerequisites**:
    
 - Java JDK 8: OpenJDK is our default but Oracle can be used too
 - Python 3: accessed as the default, check with `python --version`
 - Pip: the python package manager for various scripts
 - MongoDB v 3.x: This is used as the metadata store for Harness and may be used by engines for model storage. Is needs to be running at all times so set to start at boot.
 - IntelliJ IDEA with the Scala and Python plugins.

# Harness Server

The typical method to run and debug Harness is to launch the Sever, then trigger CLI commands with Bash run configs in IntelliJ or run the Java SDK examples, which send events and make queries. First the Server: 

1. Get source, make-distribution, un-tar distribution

    ```
    git clone https://github.com/actionml/harness.git 
    cd harness/rest-server
    ./make-distribution.sh 
    tar zxvf Harness-0.1.0-SNAPSHOT.tar.gz
    cd Harness-0.1.0-SNAPSHOT/dist/bin
    ```

2. Get the ActionML python SDK

    ```
    sudo pip install actionml
    ```
    
## Using the Harness CLI

To see a list of harness CLI, switch to the distribution's `bin` directory with something like `cd Harness-0.1.0-SNAPSHOT/dist/bin` then run:
    
```
./harness
```
    
you will see a list of commands:
    
```
harness start
   Starts the Harness server and prints configuration information. Starts all active engines.

harness stop
   Gracefully stops harness and all engines.

harness add [-c <some-engine.json> | <some-resource-id>]
   Create a new engine and set it's configuration

harness update [-c <some-engine.json> | <some-resource-id>] [-d | --data-delete] [-f | --force]
   Stops the engine, modifies the parameters and restarts the engine. WARNING: non-operational, instead perform harness delete then add

harness delete [-c <some-engine.json> | <some-resource-id>]
   Deletes engine and all data

harness train [-c <some-engine.json> | <some-resource-id>]
   In the Lambda model this trains the algorithm on all previously accumulated data. WARNING: non-operational.

harness status [[-c <some-engine.json> | <some-resource-id>]]
   Prints a status message for harness or for the engine specified. Note: prints only server status.
```
    
**Warning:** For now do not use `harness update` stick to `harness delete ...` followed by `harness add ...`
    

Restarting the Harness server using `harness stop` followed by `harness start` should restart all of the active engines in the state they were in when last stopped.

## Server Configuration

Harness has default configuration that allows it to run locally with all default setup of prerequisites. Any changes to the defaults should be made in `bin harness-env`. For instance the server can be made to listen for external connections on a new port by changing the lines:

```
export REST_SERVER_HOST=${REST_SERVER_HOST:-localhost}
export REST_SERVER_PORT=${REST_SERVER_PORT:-9090}
```

to 

```
export REST_SERVER_HOST=${REST_SERVER_HOST:-0.0.0.0}
export REST_SERVER_PORT=${REST_SERVER_PORT:-1234}
```

## Engine Configuration

Engines configured in JSON and instantiated from Templates. There are only a few required parts of the engine's JSON file and it will look like this:

```
{
  "engineId": "test_resource",
  "engineFactory": "com.actionml.templates.engine1.SomeEngine",
}
```

 - **`engineId`**: This corresponds to the resource-id in the Engine's REST API and is required to create these needed endpoints. For instance `/engine/engineId/events` will be able to take input. **Note**: The `engineId` must be unique across all Engines installed on this server and must be usable as a URI fragment. Internally in Harness or Engine code the `engineId` may be reused often to specify table names, directory names, index names, URL parameters, and other Engine specific IDs so it is advised that it be lower case with only the underscore as special character.
 - **`engineFactory`**: must supply the API contract in `core/templates/engine`, which minimally has an `initAndGet` method that takes the engine's JSON config file. Harness will create the class and pass in the entire JSON file.

Engines are where the work AI happens and are free to use any services they need, in either Kappa (Contextual Bandit) online learning mode, or Lambda (The Universal Recommender) batch learning mode. Engines are also free to use any compute engine they require like Vowpal Wabbit (Contextual Bandit), Spark + Mahout (The Universal Recommender) as well as other stores (HDFS, HBase) or compute engines (TensorFlow). Therefore any config of these Engine specific  services is done by the Engine in the engine's json file or in the services themselves.

## The Contextual Bandit

The CB is the first Harness Template and it uses Vowpal Wabbit (VW) for model creation and management. The full JSON config file looks like this:

```
{
  "engineId": "test_resource",
  "engineFactory": "com.actionml.templates.cb.CBEngine",
  "algorithm":{
    "modelName": "/Users/pat/harness/model.vw",
    "namespace": "test_resource",
    "maxIter": 100,
    "regParam": 0.0,
    "stepSize": 0.1,
    "bitPrecision": 24,
    "maxClasses": 3
  }
}
```

 - **algorithm**: This section is expected for any Engine with an algorithm and so is almost always present. These params go the the part of the Template extending `core/templates/algorith`, in this case `CBAlgorithm`
 - **modelName**: **Important**: this **must** be the same name for all CB Engines because it is used by the single instance of VW to store models. zThis is a location in the server's file systems where VW manages it's internal data.
 - **nameSpace**: **Important**: this puts the model data in a separate bucket so should be unique per Engine. We may use the resource-id for this longer term (to simplify things) so for now use it explicitly.
 - **maxIter**: a VW param for converging the classifier used. More will calculate models slower and 100 is a good default since more will lead to diminishing returns.
 - **regParam**: must be specified but no regularization is require for most uses.
 - **stepSize**: step size per iteration, 0.1 is a good default.
 - **bitPrecision**: VW allows multiple settings for precision.
 - **maxClasses**: For use in the PVR this is the max number of variants allowed.
