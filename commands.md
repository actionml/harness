# Commands

Harness includes an Admin command line interface. It runs using the Harness REST interface and can be run remotely from the Harness server.

Internal to Harness are ***Engines Instances*** that implement some algorithm and contain datasets and configuration parameters. All input data is validated by the engine, and must be readable by the algorithm. The simple form of workflow is:

 1. start server
 2. add engine
 3. input data to the engine
 4. train (for Lambda, Kappa will auto train with each new input)
 5. query 

See the workflow section for more detail.

## The Command Line Interface

Harness uses resource-ids to identify all objects in the system. The Engine Instance must have a JSON file, which contains all parameters for Harness engine management including the resource-id used as well as algorithm parameters that are specific to the Engine type and instance. All Harness global configuration is stored in `harness-env` see [Harness Config](harness_config.md) for details.

 - The file `<some-engine.json>` can be named anything and put anywhere.
 - The working copy of all engine parameters and input data is actually in a shared database. Add or update an Engine Instance to change its configuraiton. Changing the file will not update the Engine Instance. See the `add` and `update` commands. 

# Harness Start and Stop:

 - **`harness start [-f]`** starts the harness server based on configuration in `harness-env`. The `-f` argument forces a restart if Harness is already running. All other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started in the state they were in when harness was last run.

 - **`harness stop`** gracefully stops harness and all engines. If the pid-file has become out of sync, look for the `HarnessServer` process with `jps -lm` or `ps aux | grep HarnessServer` and execute `kill <pid>` to stop it.

# Engine Management

 - **`harness add <some-engine.json>`** creates and starts an Engine Instance of the type defined by the `engineFactory` parameter.
 - **`harness update <some-engine.json>`** updates an existing Engine Instance with values defined in `some-engine.json`. The Engine knows what is safe to update and may warn if some value is not updatable but this will be rare.
 - **`harness delete <some-engine-id>`** The Engine Instance will be stopped and the accumulated dataset and model will be deleted. No artifacts of the Engine Instance will remain except the `some-engine.json` file and any mirrored events.
 - **`harness import <some-engine-id> [<some-directory> | <some-file>]`** This is typically used to replay previously mirrored events or load bootstrap datasets created from application logs. It is equivalent to sending all imported events to the REST API.
 - **`harness train <some-engine-id>`** For Lambda style engines like the UR this will create or update a model. This is required for Lambda Engines before queries will return values.

# Bootstrapping With Import

Import can be used to restore backed up data but also for bootstrapping a new Engine instance with previously logged or collected batches of data. Imagine a recommender that takes in people's purchase history. This might exist in server logs and converting these to files of JSON events is an easy and reproducible way to "bootstrap" your recommender with previous data before you start to send live events. This, in effect, trains your recommender retro-actively, improving the quality of recommendations at its first startup.
