# Installing Harness Rest Server and Java SDK Examples

This guide allows you to build and install the binaries for the Harness server and Java SDK examples. Much of this is targeted at macOS (BSD Unix based).

There are 4 projects in the GitHub repo:

 1. The Harness server 
 2. java-sdk 
 3. java-sdk examples 
 4. python sdk

For a guide to using IntelliJ for debugging see [Debugging with IntelliJ](debugging_with_intellij.md) as well as set them up for debugging with IntelliJ. Much of this is targeted at macOS (BSD based).

To install on Ubuntu 14.04, which requires several extra steps&mdash;[see this page](https://github.com/actionml/harness/blob/develop/rest-server/install_ubuntu_14.md)

**Prerequisites**:
    
 - Java JDK 8: OpenJDK is our default but Oracle can be used too
 - Python 3: accessed as the default, check with `python --version`
 - Pip: the python package manager for various scripts
 - MongoDB v 3.x: This is used as the metadata store for Harness and may be used by engines for model storage. Is needs to be running at all times so set to start at boot.

# Harness Server

To get Harness and all sub-projects, build, and run binaries: 

1. Get source, make-distribution, un-tar distribution. You run the distribution not the code pulled from GitHub.

    ```
    git clone https://github.com/actionml/harness.git 
    cd harness/rest-server
    ./make-distribution.sh 
    tar zxvf Harness-0.1.0-SNAPSHOT.tar.gz
    cd Harness-0.1.0-SNAPSHOT/bin
    ```
1. To run Harness commands either put `.../Harness-0.1.0-SNAPSHOT/bin` in the path or move to the directory.

1. On macOS use something like `brew install python`, which will get Python 3. This will or should be symlinked to `/usr/local/bin/python` as the default Python. If you need to switch back change the symlink to Python 2.

2. Get the ActionML python SDK, which is needed by the harness CLI. It is occasionally released to the Python Package repositories but to get the latest version do the following:

    ```
    cd ~/harness/python-sdk
    python setup.py install
    ```
    
    OR to get from the Python Package repos run (only if not using the latest for debugging)

    ```
    sudo pip install actionml
    ```
    
## Using the Harness CLI

To see a list of harness CLI, switch to the distribution's `bin` directory with something like `cd Harness-0.1.0-SNAPSHOT/bin` then run:
    
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

harness import <some-resource-id> -i <path/to/events/json/files> 
       Imports events into the engine specified. The path can be to a directory or a single file.
       The files should contain one event per line of JSON. Files starting with '.' or '_' will be ignored.
```
    
**Warning:** `harness update` is only partially implemented, stick to `harness delete ...` followed by `harness add ...`. `harness import <engine-id> -i </path/to/event/json/files>` is a shortcut to `update` for adding new events.
    

Restarting the Harness server using `harness stop` followed by `harness start` should restart all of the active engines in the state they were in when last stopped and read any changes to serve config.

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

The environment variables can also be set external to `harness-env` if desired and these will take precedence to `harness-env` following the above syntax.

## Engine Configuration

Engines configured in JSON and instantiated from Templates. There are only a few required parts of the engine's JSON file and it will look like this:

```
{
  "engineId": "test_resource", // resource-id for REST API
  "engineFactory": "com.actionml.templates.cb.CBEngine", // only engine supported
  "mirrorType": "localfs", // optional, localfs is the only type supported
  "mirrorContainer": "/path/to/root/of/all/mirrored/files", // optional
  "algorithm"{
    ...
  }
}
```

The engine JSON file can be anywhere accessible to the CLI.

 - **`engineId`**: This corresponds to the resource-id in the Engine's REST API and is required to create these needed endpoints. For instance `/engine/engineId/events` will be able to take input. **Note**: The `engineId` must be unique across all Engines installed on this server and must be usable as a URI fragment. Internally in Harness or Engine code the `engineId` may be reused often to specify table names, directory names, index names, URL parameters, and other Engine specific IDs so it is advised that it be lower case with only the underscore as special character.
 - **`engineFactory`**: must supply the API contract in `core/templates/engine`, which minimally has an `initAndGet` method that takes the engine's JSON config file. Harness will create the class and pass in the entire JSON file.

Engines are where the work AI happens and are free to use any services they need, in either Kappa (Contextual Bandit) online learning mode, or Lambda (The Universal Recommender) batch learning mode. Engines are also free to use any compute engine they require like Vowpal Wabbit (Contextual Bandit), Spark + Mahout (The Universal Recommender) as well as other stores (HDFS, HBase) or compute engines (TensorFlow). Therefore any config of these Engine specific  services is done by the Engine in the engine's json file or in the services themselves.

## [The Contextual Bandit](the_contextual_bandit.md)

The CB is the first Harness Template and it uses Vowpal Wabbit (VW) for model creation and management. Instructions for using and configuring are in the [CB Template's docs](the_contextual_bandit.md).

