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

Set your path to include to the directory containing the `harness` script. **Note**: Commands not yet implemented in Harness are marked with a dagger character (**&dagger;**)

 - **`harness start [-f]`** starts the harness server based on configuration in `harness-env`. The `-f` argument forces a restart if Harness is already running. All other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started in the state they were in when harness was last run.

 - **`harness stop`** gracefully stops harness and all engines.

# Engine Management

 - **`harness add <some-engine-json-file>`** creates and starts an instance of the template defined in `some-engine-json-file`, which is a path to the template specific parameters file.
 - **`harness delete [<some-engine-id>]`** The engine and all accumulated data will be deleted and the engine stopped. No persistent record of the engine will remain.
 - **`harness import <some-engine-id> [<some-directory> | <some-file>]`** This is typically used to replay previously mirrored events or bootstrap events created from application logs. It is safest to import into an empty new engine since some events cause DB changes and others have no side effects.
 - **&dagger;**`harness train <some-engine-id>` in the Lambda model this trains the algorithm on all previously accumulated data.
 - **`harness status`** prints a status message for harness.
 - **`harness status engines [<some-engine-id>]`** lists all engines and stats about them, or only the engine specified.
 - **&dagger;**`harness status commands [<some-command-id>]` lists any currently active long running commands like `harness train ...`

# User and Permission Management

When using Authentication with Harness we define Users and give them Permissions. Two Permissions are predefined: client and admin. Clients are typically granted access to any number of Engines by ID and all of their sub-resources: Events and Queries. The admin has access to all parts of Harness. In order to manage Users and Permissions the User must be an admin so these commands will only work for an admin.

 - **`harness user-add [client | admin] [<some-engine-id>]`** returns the user-id and secret that gives `roleSet` access to the engine-id specified. Not that the engine-id is not needed for creating an admin user which, as a superuser, has access to all resources.
 - **`harness user-delete <some-user-id>`** removes the user and any permissions they have, in effect revoking their credentials. A warning will be generated when deleting an admin user.
 - **`harness grant <some-user-id> <some-engine-id>`** grants client or access to an engine-id for a user-id. **Note**: This is only needed if more than one engine-id is used by a "client" since creating a "client" requires an initializing engine-id. Also this is never needed for an admin user since they are created with superuser access to all resources.
 - **`harness revoke <some-user-id> [<some-engine-id> | *]`** revokes all permissions for a user-id to the engine-id(s) specified. The wild-card revokes access to all engine-ids. And error is reported if this is used to revoke admin permissions since all admins are superusers.
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

## `some-engine.json` Required Parameters

This file provide the parameters and config for anything that is Template/Engine specific like algorithm parameters or compute engine config (for instance Spark or VW, if used). Each Template comes with a description of all parameters and they're meaning. Some fields are used by the Harness framework for functions that are available to all Engines like mirroring.

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

Some special events like `$set`, `$unset`, `$delete` may cause mutable database data to be modified as they are received, while events that do not use the reserved "$" names represent an immutable event stream. That is to say sequence matters with input and some state is mutable and some immutable. In order to provide for replay or modification of the event stream, we provide mirroring of all events with no validation. This is useful if you wanted to change the params for an engine and re-create it using all past data.

To accomplish this, you must set up mirroring for the Harness Server. Once the Engine is launched with a mirrored configuration all events sent to `/engines/resource-id/events` will be mirrored to a location set in `some-engine.json`. **Note** that best practice would be to start with mirroring and then turn if off once everything is running correctly since mirroring will save all events and grow without limit, like unrotated server logs. This can easily lead to out-of-disk type errors. 

*In the future HDFS* can be used and mirrored file rotation will be implemented to solve this problem. We also allow mirroring to be enabled and disabled per engine-id.

To enable mirroring add the following to the `some-engine.json` that you want to mirror events:

    "mirrorType": "localfs", // optional, turn on a type of mirroring
    "mirrorContainer": "path/to/mirror", // optional, where to mirror input

set these in the global engine params, not in algorithm params as in the "Required Parameters" section above. 

To use mirrored files, for instance to re-run a test with different algorithm parameters:

    harness delete <some-resource-id>
    # change algo parameters in some-engine.json
    harness add </path/to/some-engine.json>
    # you cannot import from the mirrored directory since every event
    # will be also mirrored, causing an infinite loop so move them first
    mv </path/to/mirrored/events> </some/new/path>
    harness import -i </some/new/path>   

**Note**: any Event sent to an Engine will be mirrored to `$MIRROR_CONTAINER_NAME/engine-id/dd-MM-yy.json` as long as the event source app has write access to the endpoint, with no validation.

## Scenarios for Mirroring

 1. **Auto-backup**: To automatically backup all data, mirror it and periodically save the files to archival storage of some type. Then remove the mirrored file in the live system so they do not continue to accumulate.
 2. **Experimental Startup**: This is the case where input is used with several Engine configurations to get the best performance. In this case the input comes into the Engine and is moved to a non-mirror location once. As the Engine config or code is changed the data is imported and the Engine tested, without mirroring since as long as the input does not change there is no need to keep multiple copies of it.
 3. **Input Format Validation**: Early in the use of an Engine it is often useful to run Events into the Engine for validation. If there are errors the Events will need to be changed and can be re-imported for new validation. In this case all mirrored data will likely be deleted to avoid corrupting the backup, which should start once the Events pass validation.

# Importing and Bootstrapping

The `harness import </path/to/events>` command is useful when json events have been derived from past log history or from mirrored Events, perhaps from another Engine or when using a different Engine config JSON file. Importing is also the primary method for restoring backed up input data. Importing will add to the Engine's existing data and will also be mirrored so if the import is meant to be a clean start for the Engine, run `harness delete <engine-id>` then `harness add </path/to/engine/json/file>`. Deleting any accumulating or unneeded previously mirrored data is the responsibility of the user. Continuous mirroring will eventually lead to storage overflow.
