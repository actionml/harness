# Commands

PIO-Kappa includes Commands just like other REST resources. They can be fired using the REST API or via the Command Line Interface (CLI), which is similar to Apache PredictionIO. Some of the CLI fires the REST Commands endpoints other parts of the CLI hits other resource types as needed.

## REST Endpoints for Administration

Internal to pio-kappa are objects like datasets and algorithms, they are always attached to engines. The Engine instance manages all interactions with a dataset or algorithm. All input data is validated by the dataset and engine, and must be readable by the algorithm. Therefor in order to have a dataset the engine must also be defined or validation of input is not possible.

Therefor we are removing all reference to datasets from the admin and input api. 
          
    PUT /engines/
    PUT /engines/<engine-id>
        Response: Bad REST request, engines can only be written to
        by POST
    
    POST /engines/<engine-id> 
        Request Body: JSON for engine configuration engine.json file
        Response Body: description of engine-instance created. 
          Success/failure indicated in the HTTP return code
        Action: creates or modifies an existing engine, sets up algorithm 
          and dataset
        
    DELETE /engines/<engine-id>
        Action: removes the specified engine but none of the associated 
          resources like dataset but may delete model(s). Todo: this last
          should be avoided but to delete models separately requires a new
          resource type.
          
    GET /engines/<engine-id>
        Action: returns json for engine params and any status or stats
        available

    GET /commands/list?engines
        Request Body: none?
        Response Body: list of ids and stats for defined engines
        Action: gets a list and info, used for discovery of all resources 
          known by the system. This command is synchronous so no need
          to poll for updates
        
## REST Endpoints for Lambda Admin (TBD)

in addition to the commands above, Lambda style learners require not only setup but batch training. So some additional commands are needed:

    POST /commands/batch-train
        Request Body: description of which engine to train and any params
        Response Body: returns the command-id to poll via GET for
          information about command progress
        Action: will launch batch training of an <engine-id>. This 
          command is asynchronous so needs to be polled for status
          
    Delete /commands/<command-id>
        Action: attempts to kill the command by id and removes it

    GET /commands/<command-id> 
        Response Body: response body command status for asynch/long-lived command
        
## The Command Line Interface

PIO-Kappa uses resource-ids to identify all objects in the system, engines and commands. Every Template must have an engine. The Engine must have an `engine.json` file, which contains all parameters for the engine to run including algorithm parameters that are specific to the Template.

**Things to remember:** 
 - The file `<some-engine.json>` can be named anything and put anywhere so the term is only a short version of `/path/to/some/engine/json/file`. The same is true of the `config.json` for server wide config.
 - The working copy of all engine parameters and input data is actually in a shared database and so until you create a new engine (command below) or modify it, the engine config is not active and data will be rejected.
 - No command works before you start the server.

**Commands**:

Set your path to include to the directory containing the `harness` script.

 - `harness start` starts the harness server based on configuration in `harness-env`, which is expected to be in the same directory as `harness`, all other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started.
 - `harness stop` gracefully stops harness and all engines.
 - `harness add <some-engine.json>` creates and starts an instance of the template defined in `some-engine.json`, which is a path to the template specific parameters file. An error message is displayed if the engine is already defined or if the json is invalid.
 - `harness update <some-engine.json> [-d | --data-delete]` stops the engine, modifies the parameters and restarts the engine. If the engine is not defined a warning will be displayed that the engine is new and it will function just as `harness engine <some-engine.json> new` there will be an error if a . If `-d` is set this removes the dataset and model for the engine so it will treat all new data as if it were the first received. You will be prompted to delete mirrored data (see engine config for more about mirroring). This command will reset the engine to ground original state with the `-d` but no other change to the parameters in the json file.
 - `harness delete <some-engine.json>` The engine and all accumulated data will be deleted and the engine stopped. No persistent record of the engine will remain.
 - `harness import <some-engine.json> [<some-directory>]` This is typically used to replay previously mirrored events or bootstrap events created from application logs. It sends the files in the directory to the `/engine/resourse-id/events` input endpoint as if POSTing them with the SDK. If `<some-directory>` is omitted harness will attempt to use the mirrored events if defined in the engine config json. 
 - `harness train <some-engine.json>` in the Lambda model this trains the algorithm on all previously accumulated data.
 - `harness status [<some-engine.json>]` prints a status message for harness or for the engine specified.
 - `harness list engines` lists engines and stats about them
 - `harness list commands` lists any currently active long running commands like `harness train ...`

# Harness Workflow

Following typical workflow for launching and managing the PIO-Kappa server the following commands are available:

 1. Startup all needed services needed by a template. For PIO-Kappa with the Contextual Bandit this will be MongoDB only, but other Templates may require other services. Each type of engine will allows config for connecting to the services it needs in engine.json. But the services must be running before the Engine is started or it will not be able to connect to the services.

 1. Start the PIO-Kappa server but none of the component services like Spark, DBs, etc., :
        
        harness start 
        # default port if not specified in harness-env is 9090
         
 1. Create a new Engine and set it's configuration:

        harness add <some-engine.json>
        # the engine-id in the json file will be used for the resource-id
        # in the REST API
        
 1. The `<some-engine.json>` file is required above but it can be modifed with:

        harness update <some-engine.json>
        # use -d if you want to discard any previously collected data 
        # or model

 1. Once the engine is created and receiving input through it's REST `events` input endpoint any Kappa style learner will respond to the REST engine `queries` endpoint. To use a Lambda style (batch/background) style learner or to bulk train the Kappa on saved up input run:
    
        harness train <some-engine.json>
        
 1. If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-engine.json>

 1. To bring the server down:

        harness stop
        # The PID is retrieved from a location set in
        # `harness-env`. If the file is not provided

# Engine Configuration Parameters

Two files are used to initialize the pio-kappa Router and each Template.

## application.conf

In application.conf the default config for the Router is kept including what address and port it bind to, where MongoDB is, etc. This allows all specific config to be overridden in the environment. Simply adding a value for the following environment variables change the config:

    // application.conf, hard coded defaults
    mongo {
      host = "localhost"
      host = ${?MONGO_HOST}
      port = 27017
      port = ${?MONGO_PORT}
    }

To use a different host and port for MongoDB do the following before the REST-Server and Router is launched:

    export MONGO_HOST=<some-host-name>
    export MONGO_PORT=<some-port-number>
    
A full list of these will be provided in a bash shell script that sets up any overrides before launching the Router

## some-engine.json

This file provide the parameters and config for anything that is Template/Engine specific like algorithm parameters or compute engine config (for instance Spark or VW, if used). Each Template comes with a description of all parameters and they're meaning. Some fields are required by the pio-kappa framework:

    {
        "engineId": "some_resource_id"
        "engineFactory": "org.actionml.templates.name.SomeEngineFatory"
        "params": {
            "algorithm": {
                algorithm specific parameters, see Template docs
                ...
            },
            "dataset": {
                optional dataset specific parameters, see Template docs
                ...
            },
            "other": {
                any extra config can be defined by the template,
                for instance Spark conf may go here is Spark is used,
                see Template docs
                ...
            },
            ...
        }
    }
    
The `"other"` section or sections are named according to what the Template defines since the engine may use components of it's own choosing. For instance one Template may use TensorFlow, another Spark, another Vowpal Wabbit, or a Template may need a new server type that is only used by it. For instance The Universal Recommender will need an `"elasticsearch"` section. The Template will configure any component that is not part of the minimal subset defined by the Router
         
# The missing Commands

It **is** possible to take an existing dataset and reuse it with a new Engine. This is done by the `harness engine update <engine-id>` command, which changes parameters the engine uses but does not delete the data. This only works with Engines of the same type where you are only changing parameters, for example altering the algorithm parameters. You cannot create 2 engines that use the same dataset since each may be modifying over the other.

It **should** be possible to duplicate data to be used with a new engine of the exact same type, possibly differing parameters but is TBD.

To implement storage of an immutable event stream that can be re-input into a completely new Engine, which has a compatible view of input events, a streaming mechanism can be put in front of the pio-kappa server like Kafka or Flume. 

We will also implement a mechanism to stream raw events to elastic storage like HDFS before it is validated and processed by the Engine. This is so parameter changes can be applied throughout the event input or so it can be input into a new Engine with a different algorithm but which has a compatible input event formats and meaning.
