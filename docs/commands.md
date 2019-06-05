# Commands

Harness includes an Admin command line interface. It runs using the Harness REST interface and can be run remotely.

## Conventions

Internal to Harness are ***Engines Instances*** that implement some algorithm and contain datasets and configuration parameters. All input data is validated by the engine, and must be readable by the algorithm. The simple form of workflow is:

 1. start server
 2. add engine
 3. input data to the engine
 4. train (for Lambda, Kappa will auto train with each new input)
 5. query 

See the [Workflow](workflow.md) section for more detail.

Harness uses resource-ids to identify all objects in the system. The Engine Instance must have a JSON file, which contains all parameters for Harness engine management including its Engine Instance resource-id as well as algorithm parameters that are specific to the Engine type. All Harness global configuration is stored in `harness-env` see [Harness Config](harness_config.md) for details.

 - The file `<some-engine.json>` can be named anything and put anywhere.
 - The working copy of all engine parameters and input data is actually in a shared database. Add or update an Engine Instance to change its configuraiton. Changing the file will not update the Engine Instance. See the `add` and `update` commands. 

# Harness Start and Stop

Scripts that start and stop Harness are included with the project in the `sbin/`. These are used inside container startup and can be used directly in the OS level installation.

 - **`harness-start [-f]`** starts the harness server based on configuration in `harness-env`. The `-f` argument forces a restart if Harness is already running. All other commands require the service to be running, it is always started as a daemon/background process. All previously configured engines are started in the state they were in when harness was last run.

 - **`harness-stop`** gracefully stops harness and all engines. If the pid-file has become out of sync, look for the `HarnessServer` process with `jps -lm` or `ps aux | grep HarnessServer` and execute `kill <pid>` to stop it.

# Harness Administration

 - **`harnctl status [engines [<engine-id>], users [<user-id>]]`** These print status information about the objects requested. Asking for user status requires the Harness Auth-server, which is optional.
 - **`harnctl add <some-engine.json>`** creates and starts an Engine Instance of the type defined by the `engineFactory` parameter.
 - **`harnctl update <some-engine.json>`** updates an existing Engine Instance with values defined in `some-engine.json`. The Engine knows what is safe to update and may warn if some value is not updatable but this will be rare.
 - **`harnctl delete <some-engine-id>`** The Engine Instance will be stopped and the accumulated dataset and model will be deleted. No artifacts of the Engine Instance will remain except the `some-engine.json` file and any mirrored events.
 - **`harnctl import <some-engine-id> [<some-directory> | <some-file>]`** This is typically used to replay previously mirrored events or load bootstrap datasets created from application logs. It is equivalent to sending all imported events to the REST API.
 - **`harnctl export <some-engine-id> [<some-directory> | <some-file>]`** If the directory is supplied with the protocol "file:" the export will go to the harness server host's file system. This is for use with vertically scaled Harness. For more general storage use HDFS (the Hadoop File System) flagged by the protocol `hdfs` for example: `hdfs://some-hdfs-server:9000/users/<some-user>/<some-directory>`. [**to me implemented in 0.5.0**]
 - **`harnctl train <some-engine-id>`** For Lambda style engines like the UR this will create or update a model. This is required for Lambda Engines before queries will return values.

# Harness Auth-server Administration

There are several extended commands that manage Users and Role. These are only needed when using the Harness Auth-server to create secure multi-tenancy. Open multi-tenancy is the default and requires no Auth-Server
       
 - **`harnctl user-add [client <engine-id> | admin]`** Returns a new user-id and their secret. Grants the role's permissions. Client Users have access to one or more `engine-id`s, `admin` Users have access to all `engine-id`s as well as admin only commands and REST endpoints.
 - **`harnctl user-delete <user-id>`** removes all access for the `user-id`
 - **`harnctl grant <user-id> [client <engine-id> | admin]`** adds permissions to an existing user
 - **`harnctl revoke <user-id> [client <engine-id> | admin]`** removes permissions from an existing user

# Bootstrapping With Import

Import can be used to restore backed up data but also for bootstrapping a new Engine instance with previously logged or collected batches of data. Imagine a recommender that takes in people's purchase history. This might exist in server logs and converting these to files of JSON events is an easy and reproducible way to "bootstrap" your recommender with previous data before you start to send live events. This, in effect, trains your recommender retro-actively, improving the quality of recommendations at its first startup.

# Backup with Export

[**to me implemented in 0.5.0**] Lambda style Engines, which store all Events, usually support `harnctl export ...` This command will create files with a single JSON Event per line in the same format as the [Mirror](mirroring.md) function. To backup an Engine Instance use the export and store somewhere safe. These files can be re-imported for re-calculation of the input DB and, after training, the model.

Engines that follow the Kappa style do not save input but rather update the model with every new input Event. So use [Mirroring](mirroring.md) to log each new Event. In a sense this is an automatic backup that can also be used to re-instantiate a Kappa style model.
