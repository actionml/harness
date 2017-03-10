# Commands

PIO-Kappa includes Commands just like other REST resources. They can be fired using the REST API or via the Command Line Interface (CLI), which is similar to Apache PredictionIO. Some of the CLI fires the REST Commands endpoints other parts of the CLI hits other resource types as needed.

## REST Endpoints for Administration

    PUT /datasets/<dataset-id>
        Action: returns 404 since writing an entire dataset is not supported
          
    POST /datasets/
        Request Body: JSON for PIO dataset description describing Dataset
          created, must include in the JSON `"resource-id": "<some-string>"
          the resource-id is returned. If there is no `resource-id` one will be generated and returned
        Action: sets up a new empty dataset with the id specified.
        
    DELETE /datasets/<dataset-id>
        Action: deletes the dataset including the dataset-id/empty dataset
          and removes all data
          
    POST /engines/<engine-id> 
        Request Body: JSON for engine configuration engine.json file
        Response Body: description of engine-instance created. 
          Success/failure indicated in the HTTP return code
        Action: creates or modifies an existing engine
        
    DELETE /engines/<engine-id>
        Action: removes the specified engine but none of the associated 
          resources like dataset but may delete model(s). Todo: this last
          should be avoided but to delete models separately requires a new
          resource type.

    GET /commands/list?engines
    GET /commands/list?datasets
    GET /commands/list?commands
        Request Body: none?
        Response Body: list and stats for the resources requested
        Action: gets a list and info, used for discovery of all resources 
          known by the system. This command is synchronous so no need
          to poll for updates
        
## REST Endpoints for Lambda Admin

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

PIO-Kappa uses resource-ids to identify all objects in the system, datasets, engines, and commands. Every Template must have a dataset and engine. The Engine must have an `engine.json` file, which contains all parameters for the engine to run including algorithm prameters that are specific to the Template and other resource-ids that are required like a dataset resource-id.

**Things to remember:** 
 - The file `engine.json` can be named anything and put anywhere so the term is only a short version of `/path/to/some/template/json/file`. The same is true of the `config.json` for server wide config.
 - The working copy of this data is actually in a shared database and so until you create the engine (command below) or modify it, the engine config is not active. 
 - No command works before you start the server.

Following typical workflow for launching and managing the PIO-Kappa server the following commands are available:

 1. Startup all needed services. For PIO-Kappa with the Contextual Bandit this will be MongoDB only, but other Templates may require other services.

 1. Start the PIO-Kappa server but none of the component services like Spark, DBs, etc., :
        
        aml start <config.json> [-p <port-num>| --port <port-num>]
        # default port if not specified is 7071
        # config of the server is held in the config file see
        # Server Configuration section for setup.

 1. Create an empty Dataset:

        aml dataset new [<resource-id>]
        # if no id one will be generated and either way
        # the id will be printed
        
 1. Create a new Engine and set it's configuration:

        aml engine new [<resource-id>] [-c <engine.json> | --config <engine.json>]
        # if no resource-id is supplied one will be generated and 
        # printed
        
 1. Typically an `engine.json` file is supplied above but if not or if the engine config is to be modified:

        aml engine update <resource-id> [-c <engine.json> | --config <engine.json>]
        # the -c or --config must be specified, see the engine template
        # docs for engine.json requirements.

 1. Once the dataset is created and receiving input through it's REST dataset input endpoint and the Engine is created and configured, any Kappa style learner will respond to the REST engine query endpoint. To use a Lambda style (batch/background) style learner or to bulk train the Kappa on saved up input run:
    
        aml train <resource-id> # id of the engine
        
 1. If you wish to remove data to start fresh:

        aml dataset delete <resource-id> # id of dataset

 1. To remove an engine (this will not remove the dataset the engine is using, but will remove and algorithm model the engine has created):

        aml engine delete <resource-id> # id of engine  

 1. To bring the server down:

        aml stop <server-config-file> [-f | --force]
        # The PID is retrieved from a location set in the
        # Server Configuration file. If the file is not provided
        # the command will attempt to find the server process to stop
        # -f or --force will bypass the confirmation of "aml stop"
