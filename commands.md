# Commands

Harness includes an admin command line interface. It triggers the REST interface and can be run remotely as long as you point the CLI to the correct Harness server and have admin credentials. 

Harness must be running for anything but the `harness start` command to work and this is also the only command that will not work remotely. All other commands work against the running Harness server.

Internal to Harness are ***Engines*** that are instances of ***Templates*** made of objects like datasets and algorithms. All input data is validated by the engine, and must be readable by the algorithm. The simple basic form of workflow is; start server, add engine, input data to the engine, train (for Lambda, Kappa will auto train with each new input), query. See the workflow section for more detail.

## REST Endpoints for Administration 
          
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
          
    GET /
        Action: returns json for server params and any status or stats
        available. May include a list of engines active.
    
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
        Response Body: response body command status for async/long-lived command
        
## The Command Line Interface

Harness uses resource-ids to identify all objects in the system, engines and commands. The Engine must have an `some-engine.json` file, which contains all parameters for the engine to run including the resource-id used in as the "R" in REST as well as algorithm parameters that are specific to the Template. All Harness specific config is in harness-env like DB addresses, ports for the Harness REST, etc. See Harness Config for details (not written yet).

**Things to remember:** 

 - The file `<some-engine.json>` can be named anything and put anywhere so the term is only a short version of `/path/to/some/engine/json/file`. The same is true of the `config.json` for server wide config.
 - The working copy of all engine parameters and input data is actually in a shared database and so until you create a new engine (command below) or modify it, the engine config is not active and data will be rejected.
 - No command works before you start the server.
 - In order to administer Harness remotely it is often inconvenient to have the `some-engine.json` file present so most commands allow use of the resource-id as shorthand for the engine to be managed. Using the resource-id assumes that the engine has been added, which can only be accomplished with a json params file. Remote execution of the CLI can also be done with the `some-engine.json` file, which contains the resource-id.

**Commands**:

Set your path to include to the directory containing the `harness` script. Commands not implemented in Harness v0.0.1 are marked with a dagger character (&dagger;)

 - `harness start` starts the harness server based on configuration in `harness-env`, which is expected to be in the same directory as `harness`, all other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started.
 - `harness stop` gracefully stops harness and all engines.
 - `harness add -c <some-engine.json>` creates and starts an instance of the template defined in `some-engine.json`, which is a path to the template specific parameters file. An error message is displayed if the engine is already defined or if the json is invalid.
 - &dagger;`harness update [-c <some-engine.json> | <some-resource-id] [-d | --data-delete] [-f | --force]` stops the engine, modifies the parameters and restarts the engine. If the engine is not defined a warning will be displayed that the engine is new and it will function just as `harness engine <some-engine.json> new` there will be an error if a . If `-d` is set this removes the dataset and model for the engine so it will treat all new data as if it were the first received. You will be prompted to delete mirrored data unless `-f` is supplied (see engine config for more about mirroring). This command will reset the engine to ground original state with the `-d` if there are no changes to the parameters in the json file.
 - `harness delete <some-resource-id>` The engine and all accumulated data will be deleted and the engine stopped. No persistent record of the engine will remain.
 - &dagger;`harness import <some-resource-id> [<some-directory>]` This is typically used to replay previously mirrored events or bootstrap events created from application logs. It sends the files in the directory to the `/engine/resourse-id/events` input endpoint as if POSTing them with the SDK. If `<some-directory>` is omitted harness will attempt to use the mirrored events if defined in the engine config json. 
 - &dagger;`harness train [-c <some-engine.json> | <some-resource-id]` in the Lambda model this trains the algorithm on all previously accumulated data.
 - `harness status [[-c <some-engine.json> | <some-resource-id]]` prints a status message for harness or for the engine specified.
 - &dagger;`harness list engines` lists engines and stats about them
 - &dagger;`harness list commands` lists any currently active long running commands like `harness train ...`

# Harness Workflow

Following typical workflow for launching and managing the Harness server the following commands are available:

 1. Startup all needed services needed by a template. For Harness with the Contextual Bandit this will be MongoDB only, but other Templates may require other services. Each type of engine will allows config for connecting to the services it needs in engine.json. But the services must be running before the Engine is started or it will not be able to connect to the services.

 1. Start the Harness server but none of the component services like Spark, DBs, etc., :
        
        harness start 
        # default port if not specified in harness-env is 9090
         
 1. Create a new Engine and set it's configuration:

        harness add -c <some-engine.json>
        # the engine-id in the json file will be used for the resource-id
        # in the REST API
        
 1. The `<some-engine.json>` file is required above but it can be modifed with:

        harness update -c <some-engine.json> ...
        # use -d if you want to discard any previously collected data 
        # or model

    &dagger;This command is not implemented yet so use `harness delete` and `harness add` for updates but be aware that all data will be destroyed during `delete`.    

 1. &dagger;Once the engine is created and receiving input through it's REST `events` input endpoint any Kappa style learner will respond to the REST engine `queries` endpoint. To use a Lambda style (batch/background) style learner or to bulk train the Kappa on saved up input run:
    
        harness train -c <some-engine.json>
        
 1. If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-resource-id>

 1. To bring the server down:

        harness stop
        # stop may take some time and it's usually safe to 
        # just kill the harness PID

# Configuration Parameters

All config of Harness and it's component services is done with `harness-env` or in the service config specific to the services used.

## `harness-env`

`harness-env` is a Bash shell script that is sourced before any command is run. All required variable should be defined like this:

    export MONGO_HOST=<some-host-name>
    export MONGO_PORT=<some-port-number>
    
A full list of these will be provided in a bash shell script that sets up any overrides before launching Harness

## `some-engine.json` Required Parameters

This file provide the parameters and config for anything that is Template/Engine specific like algorithm parameters or compute engine config (for instance Spark or VW, if used). Each Template comes with a description of all parameters and they're meaning. Some fields are required by the Harness framework:

    {
        "engineId": "some_resource_id"
        "engineFactory": "org.actionml.templates.name.SomeEngineFatory"
        "mirrorType": "hdfs" | "localfs", // optional, turn on a type of mirroring
        "mirrorLocation": "path/to/mirror", // optional, where to mirror input
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
    
The `"other"` section or sections are named according to what the Template defines since the engine may use components of it's own choosing. For instance one Template may use TensorFlow, another Spark, another Vowpal Wabbit, or a Template may need a new server type that is only used by it. For instance The Universal Recommender will need an `"elasticsearch"` section. The Template will configure any component that is not part of the minimal subset defined by Harness.

# Input Mirroring and Importing (not implemented yet)

Some special events like `$set`, `$unset`, `$delete` may cause mutable database data to be modified as they are received, while user/usage events represent an immutable event stream. That is to say sequence matters with input and some state is mutable and some immutable. In order to provide for replay of modification of the event stream, we provide mirroring of input events with no validation. This is useful if you wanted to change the params for how an engine works and want to re-create it using all past data. 

To accomplish this, you must set up mirroring. Then the next input event sent to `/engines/resource-id/events` will be mirrored to a location set in `some-engine.json`. Best practice would be to start with mirroring and then turn if off once everything is running correctly since mirroring will save all events and grow without limit, like unrotated server logs. HDFS can be used and is recommended for mirroring.

Simply set `"mirror": "path/to/mirror"` in `some-engine.json`. The path can be on the local filesystem or HDFS by specifying the correct URI, such as `"mirror": "hdfs://some-hadoop-master:9000/user/aml/"` a directory named for the engine resource-id will be created and json files will be sent there packaged so time periods are human readable.

`harness update <resource-id> -d` will delete engine data leaving any previously mirrored data and `harness import <resource-id>` will check for the mirrored files move them, reload all the mirrored data, and leave the previously mirrored files intact. This will create 2 identical dataset directories so if the import proceeded correctly you can remove the old data, if there were errors the old events are still there to be used again, nothing is lost, `harness import <resource-id> -f ...` allows you to reimport any event json files including the events of record. 

# Importing and Bootstrapping (not implemented yet)

The `harness import` command is also useful when json events have been derived from past history available from other application databases or logs in a bootstrapping operation.

# Generating Authentication Tokens (not implemented yet)

The `harness grant <access.json>` command will grant access to the resources defined in the access.json file and generate the tokens to be used in authentication. Likewise `harness deny <token> <access.json>` will remove access rights for the token to the routes defined.

The format of the json route file is TBD as well as the method of generating tokens.
         
