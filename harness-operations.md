# Harness Operations

Harness is a server that depends on other services. Harness itself takes up few resources and does not do heavyweight tasks in its process boundary (JVM) so a good deal of operating Harness is making sure dependent services are happy.

# Create a Scalable Deployment

Harness and all its services can be deployed on a single server installed on the machine directly using [Installation Instructions](install.md).

To install and deploy in a more scalable, easy to monitor manner we suggest that all dependent services are deployed on separate machines or containers. 

These Services are:

 - **Spark**: Harness can run with `master=local` mode with Spark jobs executing in the Harness JVM but an external Spark cluster is better for scalability.
 - **HDFS**: Harness can use local file storage but HDFS allows for indefinite expansion as one node fills up, ass more.
 - **MongoDB**: The primary Engine Metadata Store and Event Store are in MongoDB. See MongoDB docs for scaling information.
 - **Elasticsearch**: This is required for several Engine types based on the Universal Recommender's Correlated Cross-Occurrence Algorithm.
 - **Vowpal Wabbit**: This is required for the Contextual Bandit Engine and is started on demand if it is used, no startup is required beyond installation.

# Startup on Start/Restart

Harness will throw exceptions visible in its logs if all services are not running when it is started. The ideal Startup order is:
 
 1. HDFS (optional, takes some time to get out of "safe mode")
 2. MongoDB
 3. Elasticsearch (for UR type Algorithms)
 4. Harness Auth-Server (optional)
 5. Harness

These services should be set to startup when the machine or container they run on is started/restarted. Harness will be able to re-establish connections to all services if any combination are restarted.

**Note:** HDFS takes several seconds after startup before it exits "safe mode" and allows data to be written. This may cause some exceptions in logs if Events are coming in that need to be mirrored to HDFS but they will go away as HDFS warms up. This may cause some events to be lost in the mirror but does not affect the Events processed by the target Engine instance.

## High Availability and Data Loss on Recover

There are several scenarios where a service failure will result in some loss of incoming data in the form of Events. All dependency services have High Availability configurations to solve this&mdash;see their online documentation for how to set this up if needed.

The impact of lost events depends on the algorithm in use so see each the Engine's documentation. An example is that if the UR does not receive an item property change event, the model will have an out-of-date item property until it is changed or the event is re-sent. The easiest way to mitigate this is to send these events periodically or use an HA event buffering system like Apache Kafka. Harness makes no attempt to solve this problem.


# Monitoring

Basic monitoring of resources is easily done if services are separated. Even in an all-in-one installation this should be the first type of monitoring:

 - Memory: This will tend to run at a steady state but drastically increase on Machines or Containers that run Spark for training. As data accumulates in Lambda Engine instances memory needs will increase. See Tuning for methods to deal with this.
 - CPU: The services will try to get as much from the cores available. Monitoring usage will help spot bottlenecks. 
 - Disk: Except for Spark where disk needs are ephemeral, services will use more and more disk. This will tend to reach a steady state except for accumulating logs and events mirrors.

To use a more sophisticated monitoring system check each service type for monitoring hooks. For instance DataDog has plugins for several of the Harness sub-services.

Harness itself does not provide hooks since its resource needs are few. 

## Memory

Three parts of an Engine instance lifecycle will have differing memory needs:

 - Input: this will stay pretty steady state no matter the input load
 - Queries: This will remain steady state once the indexes are cached in-memory for Elasticsearch and MongoDB. 
 - Training: Spark machines or containers will have memory needs based on the dataset they process. In a multi-tenant setting, where there are many instances of a Spark using Engine, it is best to allot Spark Executor and Driver memory of the maximum available on the node (after any other processes are considered). So even though many Engine instances may require only a small amount of memory, DevOps should give them all that is available for the worst case Engine instance. This will help smaller clients train faster and provide the minimum for the largest client. See Tuning for how to specify Spark memory usage

## CPU

All services in Harness are multi-tasking and so have needs for multiple cores. Since the need for Spark is ephemeral its use is the biggest change in total CPU resources needed. Spark gets scalability by spreading work over nodes with cores it can use. For an all-in-one installation watch for CPU usage during training, this will try to max the CPU resources available.

## Disk

The key uses of disk are:

 - Logs: which accumulate for all services. Some type of log rotation system should be installed to deal with this by dropping or archiving older logs. These will mostly be in the local file system of the various servers.
 - Spark Persistent Logs: The execution of a Spark job can store logs in HDFS and this is the way we debug the training of an Engine instance. These logs must be periodically cleaned up. The ephemeral Spark logs (when not writing them to HDFS) are cleaned after each Job finishes. See Tuning for how to switch on/off Spark Persistent Logs. 
 - Event Mirrors: The mirror is a special type of log that is also directly importable into an engine instance. There are mechanisms in Engine instances that manage DB usage but mirrors grow forever if not trimmed or archived. Harness does nothing to solve this.

# Tuning

Operational tuning is primarily about managing resources needed by services. Tuning of services is best done by referencing the service documentation. The major exception is Spark, which is tuned to a large degree based on algorithm and data needs.

## Persistent Services

 - MongoDB: [Operations Manual](https://docs.mongodb.com/manual/administration/production-checklist-operations/)
 - Elasticsearch: [Tuning for Search Performance](https://www.elastic.co/guide/en/elasticsearch/reference/current/tune-for-search-speed.html#tune-for-search-speed)
 - HDFS: The primary parameter that effects HDFS performance and resource usage (beyond total data stored) is replication. Keeping 3 copies of every block allows for data recovery in almost any failure situation but if you only have one HDFS node, it only slows performance. See the replication setting for [HDFS here](https://hadoop.apache.org/docs/r1.2.1/hdfs_design.html#Data+Replication) Setting dfs.replication in the file hadoop/etc/hadoop/hdfs-site.xml is the easiest way to affect this. A setting of 1 is best for an all-in-one harness installation, 3 is good for a resilient HDFS cluster.

    ```
    <!-- Put site-specific property overrides in this file. -->
    
    <configuration>
        <property>
            <name>dfs.replication</name>
            <value>1</value>
        </property>
        <property>
          <name>hadoop.tmp.dir</name>
          <value>/data/hadoop</value>
          <description>A base for other temporary directories.</description>
      </property>
    </configuration>
    ```

## Spark

Spark is mostly tuned on a Job by Job basis so most tuning is done through an Engine's JSON config file in the `sparkConf` section.

### Spark Job Requirements

Memory requirements are most often tuned since data will change over time. There is no easy way to tell how much memory Spark needs to process a job. Spark needs to keep in-memory all data that is working on at every stage of the Job. This memory need is spread out over all Spark nodes. For an all-in-one Harness deployment this will mean that the worst case memory needs will be during training of the largest Engine Instance. 

Typical `sparkConf` section of an Engine instance's JSON configuration parameters:

```
"sparkConf": {
    "master": "local",
    "spark.driver-memory": "4g",
    "spark.executor-memory": "4g",
    "spark.serializer": "org.apache.spark.serializer.KryoSerializer",
    "spark.kryo.registrator": "org.apache.mahout.sparkbindings.io.MahoutKryoRegistrator",
    "spark.kryo.referenceTracking": "false",
    "spark.kryoserializer.buffer": "300m",
    "es.index.auto.create": "true"
},

```

For "real" datasets `spark.driver-memory` and `spark.executor-memory` should be set to the available memory on the node. This must be enough memory to process the largest dataset for the particular Harness instance. 4g is only enough to process small test datasets like the ones we use for integration tests, a more typical number will be 16-32g. And this is for the Driver AND Executor so you'll have to have driver-memory + executor-memory available. On an all-in-one deployment vertical scaling will be inefficient. Especially since Spark only uses this memory when training a model.

The rest of the `sparkConf` is set to pass information to the Job and above is correct for the UR style Engines.

# Lifecycle Operations

Every Engine has a lifecycle and every dataset and model have a workflow. The Engine instance lifecycle is:

## Engine Instance Lifecycle

 - `harness add <path/to/some-engine.json>`
 - `harness update <path/to/some-engine.json>`
 - `harness delete <some-engine-id>`

This creates an Engine instance to receive data via Events or from importing batch data. The Engine can be updated to change algorithm parameters or other settings like those in `sparkConf`. The `harness update` command has no effect on accumulated data or any existing model for the engine, it only changes things specified in the JSON config file. Then we delete the Engine instance, which removes all internal data but that stored in mirrored event logs.

The Engine's JSON should contain a unique `engineId` or Harness will report an error when adding the instance.

```
"engineId": "ur_nav_hinting",
"engineFactory": "com.actionml.engines.ur.UREngine",
```

The `engineId` is the resource-id used in the REST API. The factory is specific to the Engine type.

## Dataset and Model Workflow

Kappa style Engines, are very simple to operate since they manage their own model updates in real time. A Kappa workflow looks like:

 - input data
 - query
 - repeat each in parallel, a query may work after the first input depending on the algorithm 

Lambda style Engines require periodic re-training. Queries need models so until the first time training succeeds for a Lambda Engine queries will return empty results. A Lambda Engine's workflow look like:

 - input data via live events or importing
 - `harness train <some-engine-id>` trains a model. Until this is done queries will return empty
 - query
 - repeat all of the above in parallel

Since Lambda models are updated periodically, query results tend to change the most after training. However real time input is used to some extent in Lambda queries so training need not be done with extreme frequency. For instance if the UR is being used for a mature E-Commerce application the model needs to be updated proportional to how fast the inventory changes. For many apps this might mean weekly training, for other types of "inventory" training may be needed more often.

## Lambda Style Model Update Automation

Periodic updates of Lambda models can be done by running `harness train <some-engine-id>`. This can be automated with something like Cron to be executed for each engine at some optimal interval.

**Note:** `harness train` may put extra load on the event and model store so even though it will execute in parallel with input and query, it is best to plan for this extra load, perhaps by training at low demand times.

## Non-issues with Harness Model Updates

Whether models are Kappa or Lambda they are updated and made available for queries without stopping the incoming Events or Queries. `harness train` can be executed at any time.

# Onboarding a new Engine Instance

While Harness is installed and running we can onboard a new Engine instance. The process is:

 1. Decide on an `engineId` for the Engine instance and create a JSON file for configuration. See the specific Engine for configuration parameters
 - `harness add <path/to/some-engine.json>` to add the instance based on the JSON configuration with the `engineId` and `engineFactory` in the file
 - `harness import <path/to/import-data>` can optionally be used to bootstrap the Engine instance.
 - Send Events to the Engine instance via the REST API
 - `harness train <some-engine-id>` makes a Lambda model available for queries, Kappa does not require this.
 - send queries to Harness
 - repeat any steps above from #3 on as needs dictate