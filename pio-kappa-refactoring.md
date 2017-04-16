# Important changes in REST API and CLI

**Datasets are removed from the APIs** Due to the fact an Engine is required to validate input and that some input operates on mutable objects, datasets cannot be thought of as immutable event streams so they are not shareable by engines. Therefore they are removed from the REST and command API. They remain as-is in the current code base.

# New input REST API

 - **engines**: All data is sent to an engine and queries are made of an engine. Input is validated based on the needs of the dataset, which is constructed to fit requirements of the algorithm.
 - **events**: a sub-collection of a particular engine. They are addressed like `POST /engines/<engine-id>/events/` for adding. Events are loosely defined in JSON with engine specific fields. Unreserved events (no $ in the name) can be thought of as a non-ending stream. Reserved events like $set may cause properties of mutable objects to be changed immediately upon being received and may even alter properties of the model. See the engine description for how events are formatted and handled.
 - **commands**: pre-defined commands that perform workflow or administrative tasks. These may be synchronous, returning results with the HTTP response or asynchronous, where they must be polled for status since the command may take very long to complete.

## Input and Query

See the Java SDK for more specifics. There are 2 primary APIs in the SDK for sending PIO events and making queries. Both reference an engine-id but have different endpoints.

    POST /engines/<engine-id>/events
        Request Body: JSON for PIO event
        Response Body: na
        
    POST /engines/<engine-id>/queries
        Request Body: JSON for PIO query
        Response Body: JSON for PIO PredictedResults

# New Admin API

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

## The Command Line Interface

PIO-Kappa uses resource-ids to identify all objects in the system, engines and commands. Every Template must have an engine. The Engine must have an `engine.json` file, which contains all parameters for the engine to run including algorithm parameters that are specific to the Template.

**Things to remember:** 
 - The file `engine.json` can be named anything and put anywhere so the term is only a short version of `/path/to/some/engine/json/file`. The same is true of the `config.json` for server wide config.
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
        
    