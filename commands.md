# Commands

Harness includes an Admin command line interface. It triggers the REST interface and can be run remotely from the Harness server

Internal to Harness are ***Engines Instances*** that are instances of ***Engines*** made of objects like datasets and algorithms, plus a specific configuration. All input data is validated by the engine, and must be readable by the algorithm. The simple form of workflow is:

 1. start server
 2. add engine
 3. input data to the engine
 4. train (for Lambda, Kappa will auto train with each new input)
 5. query 

See the workflow section for more detail.

## The Command Line Interface

Harness uses resource-ids to identify all objects in the system. The Engine must have a `<some-engine-json>` file, which contains all parameters for Harness engine management including the resource-id used as well as algorithm parameters that are specific to the Engine. All other Harness specific config is in `harness-env.sh` like DB addresses, ports for the Harness REST API, etc. See [Harness Config](harness_config.md) for details.

 - The file `<some-engine-json-file>` can be named anything and put anywhere.
 - The working copy of all engine parameters and input data is actually in a shared database and so until you add or update an engine instance, the engine config is not active and event data sent to an inactive engine-id will be rejected.

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
 - **`harness train <some-engine-id>`** in the Lambda model this trains the algorithm on all previously accumulated data.
 - **`harness status`** prints a status message for harness.
 - **`harness status engines [<some-engine-id>]`** lists all engines and stats about them, or only the engine specified.

If you are using Auth, see [Users and Roles](users_and_roles.md)

# Bootstrapping With Import

Import can be used to restore backed up data but also for bootstrapping a new Engine instance with previously logged or collected batches of data. Imagine a recommender that takes in people's purchase history. This might exist in server logs and converting these to files of JSON events is an easy and reproducible way to "bootstrap" your recommender with previous data before you start to send live events. This, in effect, trains your recommender retro-actively, improving the quality of recommendations at its first startup. This is one popular solution to the "cold-start" problem but can be used to bootstrap any Engine with training data.  

The `harness import </path/to/events>` command is one way to read JSON events directly. It is equivalent to sending Events via the REST input API. 