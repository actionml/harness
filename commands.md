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
 - The file `engine.json` can be named anything and put anywhere so the term is only a short version of `/path/to/some/engined/json/file`. The same is true of the `config.json` for server wide config.
 - The working copy of this data is actually in a shared database and so until you create the engine (command below) or modify it, the engine config is not active. 
 - No command works before you start the server.

Following typical workflow for launching and managing the PIO-Kappa server the following commands are available:

 1. Startup all needed services needed by a template. For PIO-Kappa with the Contextual Bandit this will be MongoDB only, but other Templates may require other services. Each type of engine will allows config for connecting to the services it needs in engine.json. But the services must be running before the Engine is started or it will not be able to connect to the services.

 1. Start the PIO-Kappa server but none of the component services like Spark, DBs, etc., :
        
        aml start <config.json> [-p <port-num>| --port <port-num>]
        # default port if not specified is 9090
        # config of the server is held in the config file see
        # Server Configuration section for setup.
        
 1. Create a new Engine and set it's configuration:

        aml engine new [<resource-id>] [-c <engine.json> | --config <engine.json>]
        # if no resource-id is supplied one will be generated and 
        # printed
        
 1. The `engine.json` file is required above but it can be modifed with:

        aml engine update <resource-id> [-c <engine.json> | --config <engine.json>]
        # the -c or --config must be specified, see the engine template
        # docs for engine.json requirements.

 1. Once the engine is created and receiving input through it's REST `events` input endpoint any Kappa style learner will respond to the REST engine `queries` endpoint. To use a Lambda style (batch/background) style learner or to bulk train the Kappa on saved up input run:
    
        aml train <resource-id> # id of the engine
        
 1. If you wish to **remove all data** and the engine to start fresh:

        aml engine delete <resource-id> # id of engine

 1. To bring the server down:

        aml stop <server-config-file> [-f | --force]
        # The PID is retrieved from a location set in the
        # Server Configuration file. If the file is not provided
        # the command will attempt to find the server process to stop
        # -f or --force will bypass the confirmation of "aml stop"

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

## engine.json

This file provide the parameters and config for anything that is Template/Engine specific like algorithm parameters or compute engine config (for instance Spark or VW, if used). Each Template comes with a description of all parameters and they're meaning. Some fields are required by the pio-kappa framework:

    {
        "engineId": "test_resource"
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

It **is** possible to take an existing dataset and reuse it with a new Engine. This is done by the `aml engine update <engine-id>` command, which changes parameters the engine uses but does not delete the data. This only works with Engines of the same type where you are only changing parameters, for example altering the algorithm parameters. You cannot create 2 engines that use the same dataset since each may be modifying over the other.

It **should** be possible to duplicate data to be used with a new engine of the exact same type, possibly differing parameters but is TBD.

To implement storage of an immutable event stream that can be re-input into a completely new Engine, which has a compatible view of input events, a streaming mechanism can be put in front of the pio-kappa server like Kafka or Flume. 

We will also implement a mechanism to stream raw events to elastic storage like HDFS before it is validated and processed by the Engine. This is so parameter changes can be applied throughout the event input or so it can be input into a new Engine with a different algorithm but which has a compatible input event formats and meaning.
