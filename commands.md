# Commands

Harness includes an admin command line interface. It triggers the REST interface and can be run remotely as long as you point the CLI to the correct Harness server. The only exception is Starting and Stoping Harness, which must be done on the machine Harness runs on.

Harness must be running for all but the `harness start` command. All other commands work against the running Harness server.

Internal to Harness are ***Engines Instances*** that are instances of ***Engines*** made of objects like datasets and algorithms, plus a specific configuration. All input data is validated by the engine, and must be readable by the algorithm. The simple form of workflow is; start server, add engine, input data to the engine, train (for Lambda, Kappa will auto train with each new input), query. See the workflow section for more detail.

## The Command Line Interface

Harness uses resource-ids to identify all objects in the system, engines and commands. The Engine must have a `<some-engine-json-file>` file, which contains all parameters for Harness engine management including the resource-id used as well as algorithm parameters that are specific to the Template. All other Harness specific config is in `harness-env.sh` like DB addresses, ports for the Harness REST API, etc. See [Harness Config](harness_config.md) for details.

**Things to remember:** 

 - The file `<some-engine-json-file>` can be named anything and put anywhere. `harness-env.sh` contains parameter overrides and must be in `/path/to/harness/bin/harness-env.sh`.
 - The working copy of all engine parameters and input data is actually in a shared database and so until you add or update an engine instance, the engine config is not active and event data sent to an inactive engine-id will be rejected.
 - No command works before you start the server except for `harness start`

# Harness Start and Stop:

Set your path to include to the directory containing the `harness` script. **Note**: Commands not yet implemented in Harness are marked with a dagger character (**&dagger;**)

 - **`harness start [-f]`** starts the harness server based on configuration in `harness-env`. The `-f` argument forces a restart if Harness is already running. All other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started in the state they were in when harness was last run.

 - **`harness stop`** gracefully stops harness and all engines. If the pid-file has become out of sync, looks for the `Main` process for Harness with `jps -lm` and execute `kill <pid>` to stop it.

See the [Harness REST-Spec](rest_spec.md) for all HTTP APIs. These are used by the CLI.

# Engine Management

 - **`harness add <some-engine-json-file>`** creates and starts an instance of the template defined in `some-engine-json-file`, which is a path to the template specific parameters file.
 - **`harness update <some-engine-json-file>`** updates an existing engine with values defined in `some-engine-json-file`, which is a path to the template specific parameters file. The Engine knows what is safe to update for example as of Harness 0.1.1, only updates to mirroring and a shared user DB are supported. To find what got updated do a `harness status engine ...` to get the current list of params used.
 - **`harness delete <some-engine-id>`** The engine and all accumulated data will be deleted and the engine stopped. No persistent record of the engine will remain.
 - **`harness import <some-engine-id> [<some-directory> | <some-file>]`** This is typically used to replay previously mirrored events or bootstrap events created from application logs. It is safest to import into an empty new engine since some events cause DB changes and others have no side effects.
 - **&dagger;**`harness train <some-engine-id>` in the Lambda model this trains the algorithm on all previously accumulated data.
 - **`harness status`** prints a status message for harness.
 - **`harness status engines [<some-engine-id>]`** lists all engines and stats about them, or only the engine specified.
 - **&dagger;**`harness status commands [<some-command-id>]` lists any currently active long running commands like `harness train ...`

# User and Permission Management

When using Authentication with Harness we define Users and give them Permissions. Two Permissions are predefined: client and admin. Clients are typically granted access to any number of Engines by ID and all of their sub-resources: Events and Queries. The admin has access to all parts of Harness. In order to manage Users and Permissions the User must be an admin so these commands will only work for an admin.

 - **`harness user-add [client <engine-id> | admin]`** returns the user-id and secret that gives client access to the engine-id specified OR gives the user-id admin global superuser access.
 - **`harness user-delete <some-user-id>`** removes the user and any permissions they have, in effect revoking their credentials. A warning will be generated when deleting an admin user.
 - **`harness grant <user-id> [client <engine-id> | admin]`** modifies some existing user-id's access permissions, including elevating the user to admin super user status.
 - **` harness revoke <user-id> [client <engine-id>| admin]`** revokes some existing permission for a user
 - **`harness status users [<some-user-id>]`** list all users and their permissions or only permissions for requested user.

# Harness Workflow

Startup all services needed by all templates. For Harness with the Contextual Bandit this will be MongoDB and Vowpal Wabbit (see the Template docs for installing services). Other Templates may require other services. Each type of engine will allow config for connecting to the services it needs in its JSON config file. But the services must be running before Harness and the Engine is started or it will not be able to connect to them.

## Workflow Without Auth

 1. Start the Harness server after the component services like Spark, DBs, etc:
        
        harness start 
         
 1. Create a new Engine and set it's configuration:

        harness add </path/to/some-engine.json>
        # the engine-id in the JSON file will be used for the resource-id
        # in the REST API
        
 1. The end user sends events to the engine-id using the SDK and the engine does what it does. Some engines (Kappa style) will update the model in real time, others (Lambda style) will save up data until they are told to "train" when the model will be completely updated.
        
 1. **&dagger;**Once the engine is created and receiving input Events, any Kappa style learner will respond to queries so skip to the next step. To use a Lambda style (batch/background) style learner run:
    
        harness train <some-engine-id>
        # creates or updates the engine's model

 1. Once the Engine has data and has created or updated its model Harness will respond to queries.    
 1. If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-engine-id>

 1. To bring the server down:

        harness stop
        # stop may take some small amount of time
        
## Enabling Auth

See instructions in [Security](security.md)
 
## Workflow With Auth   

The primary difference between using the CLI with auth and without is that users are not required without auth and the Auth Server is not launched. Any access to the resources of Harness is allowed without auth. So enabling auth requires at least 2 users, one "admin" and one "client". The remote user of the SDK will usually be a "client" accessing one or more engine-ids via the Events and Queries resource types. The "admin" user runs the CLI.

**Warning**: It is unsafe to use Auth without TLS/SSL enabled since the user secret credentials (Bearer Token) can be seen in clear text by any man-in-the-middle attack. See [Security](security.md) for a description of how to enable server side TLS and OAuth2.

In the above workflow when using the Auth-Server we will need to create a user and grant them "client" access to the Engine once it is created. The Bearer Token would be used to construct the Java or Python SDK client such as the `EventsClient` and the `QueriesClient`. In this sense the secret/ OAuth2 Bearer Token acts as credentials.   

 1. Create the Admin user and enable auth with TLS as described in the section above.
 
 1. Start the Harness server **after** the component services like MongoDB and others required by the templates used:
        
        harness start 
         
 1. Create a new Engine and set it's configuration:

        harness add </path/to/some-engine.json>
        # the engine-id in the json file will be used for the resource-id
        # in the REST API
 
 1. With Auth in place we must create a user with credentials then grant them client access to the engine-id:

        harness user-add client <some-engine-id>
        # a user-id and secret will be returned   
        
 At this point the admin is expected to send the user-id and secret to the end user so they can use it with the SDK.
 
 1. The end user sends events to the engine-id using the SDK and the engine does what it does. Some engines (Kappa style) will update the model in real time, others (Lambda style) will save up data until they are told to "train" when the model will be completely updated.
        
 1. **&dagger;**Once the engine is created and receiving input Events, any Kappa style learner will respond to queries so skip to the next step. To use a Lambda style (batch/background) style learner run:
    
        harness train <some-engine-id>
        # creates or updates the engine's model

 1. Once the Engine has data and has created or updated its model Harness will respond to queries.    
        
 1. If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-engine-id>
 
 1. If the end user only has access to this resource they will no linger be able to use Harness but if you wish to refuse connections you may also want to remove the user with:

        harness user-delete <some-user-id>    

 1. To bring the server down:

        harness stop
        # stop may take some small amount of time

# Configuration Parameters

All config of Harness is done with `harness-env`. Any needed services are to be started and configured in their usual way. `harness-env` only tells Harness how to use the services, Harness does not manage them and only requires a DB for User and Engine metadata.

## `harness-env`

`harness-env` is a Bash shell script that is sourced before any command is run. All required variables can be overridden here if needed:

    export MONGO_HOST=<some-host-name>
    export MONGO_PORT=<some-port-number>
    
A full list of these will be provided in a `harness-env` and `auth-server-env`.

## `some-engine.json` Engine Parameters

This file provide the parameters and config for anything that is Engine specific like algorithm parameters or compute engine config (for instance Spark or VW, if used). Each Template comes with a description of all parameters and they're meaning. Some fields are used by the Harness framework for functions that are available to all Engines like mirroring.

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
            "other": {
                any extra config can be defined by the template,
                for instance Spark conf may go here is Spark is used,
                see Template docs
                ...
            },
            ...
        }
    }
    
The `"other"` section or section(s) are named according to what the Template defines since the engine may use components of it's own choosing. For instance one Template may use TensorFlow, another Spark, another Vowpal Wabbit. For instance The Universal Recommender will need an `"elasticsearch"` section. This config tells the Engine how to use the Service but if it must be launched, this must be done before the Engine is added to a running Harness Server and is outside of `harness start`.

# Input Mirroring

Harness will mirror all events with no validation, when configured to do so for a specific Engine instance. This is useful if you wanted to be able to backup/restore all data or are experimenting with changes in engine parameters and wish to recreate the models using past mirrored data.

To accomplish this, you must set up mirroring for the Harness Server. Once the Engine is launched with a mirrored configuration all events sent to `POST /engines/<engine-id>/events` will be mirrored to a location set in `some-engine.json`. **Note** Events will be mirrored until the config setting is changed and so can grow without limit, like unrotated server logs.  

To enable mirroring add the following to the `some-engine.json` for the engine you want to mirror events:

    "mirrorType": "localfs", // optional, turn on a type of mirroring
    "mirrorContainer": "path/to/mirror", // optional, where to mirror input

set these in the global engine params, not in algorithm params as in the "Base Parameters" section above. 

## Scenarios for Mirroring

Mirroring is similar to logging. Each new events if received by Harness and copied into a file. This is before any validation, a simple log. The format is JSON one event per line. One primary use of mirrored data is to import into an Engine instance later.

### Auto-Backup/Restore Senario

 - Setup mirroring in you Engine's JSON config.
 - Periodically archive the mirrored data and erase the files backed up. Be careful to only archive up to the current date since it is still receiving updates so only archive from the previous day backwards
 - To restore an engine instance for backed up data use `harness import </path/to/backed/up/events/directory>`. Specifying the directory will cause all event files to be imported.

### Experimental Startup Scenario

 - Add an engine to Harness using mirroring, this will put all events into the location specified. We'll call this `engine_1`
 - When you want to try different settings just create a new engine with a new config JSON file making sure to use a **new engine-id**. We'll call this `engine_2`. This will allow you to use the mirrored events as input to the new `engine_2` instance directly from where they were mirrored.
 - Now use `harness import <some-directory-of-event-files>` to batch import the events from `engine_1` into the new `engine_`  

To do this perform the following sequence:

    # create a new JSON config file for `engine_2`
    harness add </path/to/engine_2/config/json-file>
    
you cannot import from the mirrored directory since every imported event will be also mirrored, causing an infinite loop so make sure the new config file give the engine-id of `engine_2`

    harness import </some/path/to/mirror/directory/engine_1>   

at this point you will have a new engine with a new engine-id but the model will be created using the new configuration params even though the events will have the same data.

# Bootstrapping With Import

Import can be used to restore backed up data but also for bootstrapping a new Engine instance with previously logged or collected batches of data. Imagine a recommender that takes in people's purchase history. This might exist in server logs and converting these to files of JSON events is an easy and reproducible way to "bootstrap" your recommender with previous data before you start to send live events. This, in effect, trains your recommender retro-actively, improving the quality of recommendations at its first startup. This is one popular solution to the "cold-start" problem but can be used to bootstrap any Engine with training data.  

The `harness import </path/to/events>` command is one way to read JSON events directly. It is equivalent to sending Events via the REST input API. 