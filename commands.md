# Commands

Harness includes an admin command line interface. It triggers the REST interface and can be run remotely as long as you point the CLI to the correct Harness server and have admin credentials. The only exception is Starting and Stoping Harness, which must be done on the machine Harness runs on.

Harness must be running for all but the `harness start` command. All other commands work against the running Harness server.

Internal to Harness are ***Engines*** that are instances of ***Templates*** made of objects like datasets and algorithms. All input data is validated by the engine, and must be readable by the algorithm. The simple basic form of workflow is; start server, add engine, input data to the engine, train (for Lambda, Kappa will auto train with each new input), query. See the workflow section for more detail.

## REST Endpoints for the CLI 

See the [Harness REST-Spec](rest_spec.md) for all HTTP APIs. These are used by the CLI.

## The Command Line Interface

Harness uses resource-ids to identify all objects in the system, engines and commands. The Engine must have a `<some-engine-json-file>` file, which contains all parameters for Harness engine management including the resource-id used as well as algorithm parameters that are specific to the Template. All other Harness specific config is in `harness-env.sh` like DB addresses, ports for the Harness REST API, etc. See [Harness Config](harness_config.md) for details.

**Things to remember:** 

 - The file `<some-engine-json-file>` can be named anything and put anywhere. `harness-env.sh` contains parameter overrides and must be in `/path/to/harness/bin/harness-env.sh`.
 - The working copy of all engine parameters and input data is actually in a shared database and so until you create a new engine or modify it, the engine config is not active and event data sent to the resource-id will be rejected.
 - No command works before you start the server except for `harness start`

**Commands**:

Set your path to include to the directory containing the `harness` script. Commands not implemented in Harness are marked with a dagger character (**&dagger;**)

 - **`harness start`** starts the harness server based on configuration in `harness-env`, which is expected to be in the same directory as `harness`, all other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started in the state they were in when harness was last run.

 - **`harness stop`** gracefully stops harness and all engines.

# Engine Management

 - **`harness add <some-engine-json-file>`** creates and starts an instance of the template defined in `some-engine-json-file`, which is a path to the template specific parameters file.
 - **`harness delete [<some-resource-id>]`** The engine and all accumulated data will be deleted and the engine stopped. No persistent record of the engine will remain.
 - **`harness import <some-resource-id> [<some-directory> | <some-file>]`** This is typically used to replay previously mirrored events or bootstrap events created from application logs. It is safest to import into an empty new engine since some events cause DB changes and others have no side effects. **Note**: `-i` may be required before the file or directory name. run `harness help` for current implementation.
 - **&dagger;**`harness train [-c <some-engine-json-file> | <some-resource-id>]` in the Lambda model this trains the algorithm on all previously accumulated data.
 - **`harness status`** prints a status message for harness.
 - **`harness status -a`** lists all engines and stats about them.  **Note**: this will be changed to `harness status engines`
 - **`harness status <engine-id>`** status of and Engine.
 - **&dagger;**`harness status commands` lists any currently active long running commands like `harness train ...`

# User and Permission Management

When using Authentication with Harness we define Users and give them Permissions. Two Permissions are predefined: client and admin. Clients are typically granted access to any number of Engines by ID and all of their sub-resources: Events and Queries. The admin has access to all parts of Harness. In order to manage Users and Permissions the User must be an admin so these commands will only work for an admin.

 - **`harness user-add`** returns the bearer token credentials for the user and a unique user-id.
 - **`harness user-delete <user-id>`** removes the user and any permissions they have, in effect revoking their bearer token.
 - **`harness grant <user-id> [client | admin] [<engine-id> \| *]`** grants client or admin access to an engine for a user-id
 - **`harness revoke <user-id> [<engine-id> \| *]`** revokes all permissions for the engine-id
 - **`harness status users`** list all users and their permissions

# Harness Workflow (no auth)

Following typical workflow for launching and managing the Harness server the following commands are available:

 1. Startup all needed services needed by a template. For Harness with the Contextual Bandit this will be MongoDB only, but other Templates may require other services. Each type of engine will allows config for connecting to the services it needs in engine.json. But the services must be running before the Engine is started or it will not be able to connect to the services.

 1. Start the Harness server but none of the component services like Spark, DBs, etc., :
        
        harness start 
        # default port if not specified in harness-env is 9090
         
 1. Create a new Engine and set it's configuration:

        harness add <some-engine.json>
        # the engine-id in the json file will be used for the resource-id
        # in the REST API
        
 1. **&dagger;**Once the engine is created and receiving input through it's REST `events` input endpoint any Kappa style learner will respond to the REST engine `queries` endpoint. To use a Lambda style (batch/background) style learner or to bulk train the Kappa on saved up input run:
    
        harness train <some-engine.json>
        
 1. If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-engine.json>

 1. To bring the server down:

        harness stop
        # stop may take some time and it's usually safe to 
        # just kill the harness PID

# Authorization

In the above workflow when using the Auth-Server we would have to create a user and grant them client access to the Engine once it is created. The Bearer Token would be used to construct the Java or Python SDK client for the resource-type, such as the EventsClient and the QueriesClient. In this sense the Bearer Token acts as credentials.   
    
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
        "mirrorContainer": "path/to/mirror", // optional, where to mirror input
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

# Input Mirroring

Some special events like `$set`, `$unset`, `$delete` may cause mutable database data to be modified as they are received, while events that do not use the reserved "$" names represent an immutable event stream. That is to say sequence matters with input and some state is mutable and some immutable. In order to provide for replay or modification of the event stream, we provide mirroring of all events with no validation. This is useful if you wanted to change the params for an engine and re-create it using all past data.

To accomplish this, you must set up mirroring for the Harness Server. Once the server is launched with a mirrored configuration all events sent to `/engines/resource-id/events` will be mirrored to a location set in `some-engine.json`. Best practice would be to start with mirroring and then turn if off once everything is running correctly since mirroring will save all events and grow without limit, like unrotated server logs. 

In the future HDFS can be used and mirrored file rotation will be implemented to solve the problem. We also allow mirroring to be enabled and disabled per engine-id.

To enable add the following to the `some-engine.json` that you want to mirror events:

    "mirrorType": "hdfs" | "localfs", // optional, turn on a type of mirroring
    "mirrorContainer": "path/to/mirror", // optional, where to mirror input

set these in the global engine params, not in algorithm params as in the "Required Parameters" section above. 

To use mirrored files, for instance to re-run a test with different algorithm parameters:

    harness delete <some-resource-id>
    # change algo parameters in some-engine.json
    harness add -c <some-engine.json>
    # you cannot import from the mirrored directory since every event
    # will be also mirrored, causing an infinite loop so move them first
    mv </path/to/mirrored/events> <some-new-path>
    harness import -i <some-new-path>   

**Note**: any event sent to `POST /engines/<engine-id>/events` will be mirrored to `$MIRROR_CONTAINER_NAME/engine-id/dd-MM-yy.json` as long as the POSTing app has write access to the endpoint even if there is no Engine installed at that resource-id yet (`harness add -c <engine.json>` had not been run).



# Importing and Bootstrapping (not implemented yet)

The `harness import` command is useful when json events have been derived from past log history or from events mirrored from another Engine.

# Generating Authentication Tokens (not implemented yet)

The `harness grant <access.json>` command will grant access to the resources defined in the access.json file and generate the tokens to be used in authentication. Likewise `harness deny <token> <access.json>` will remove access rights for the token to the routes defined.

The format of the json route file is TBD as well as the method of generating tokens.
         
