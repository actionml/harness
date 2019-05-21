# Engine Configuration

Engines implement Machine Learning Algorithms. An Engine + config = and Engine Instance. The config for all Engines is broken into generic setting that may b e used by any Engine or are functions of Harness and Engine specific settings that may be algorithm parameters or use to control how the Engine uses other parts of the system.

# Configuration JSON

All Engine Instances must have a [Harness JSON file](harness_json.md) (a superset of JSON) to configure them. These setting initialize the Engine Instance and are not looked at again. If changes are needed the settings must be updated since they are actually stored in a DB. This file is only looked at when you `add` or `update` an Engine Instance.

The file will contain different sections for different parts of the system laid out like this:

```
{
    // Generic settings like "engineId" are outside
    // of any section
    "sparkConf": { // optional if the engine uses Spark
        ...
    },
    "dataset": { // optional data settings
        ...
    },
    algorithm: { // optional algorithm parameters
        ...
    }
    //optional other sections
}
```

# Generic Settings

These settings contain some required values or provide ways to configure all Engines.

```
{
    "engineId": "<some-engine-id>",
    "engineFactory": "com.actionml.engines.<some-engine-type>.<some-engine-class>",
    "mirrorType": "localfs" | "hdfs",
    "mirrorContainer": "< directory for storage of mirrored events >",
    "modelContainer": "< directory for storage of models >",
    "sharedDBName": "<some-db-name>",
}
```

 - **engineId:** Required, must be a possible URI fragment so lower case using snake_case is suggested.
 - **engineFactory:** Required, must be the fully qualified JVM class name associated with a particular Engine type. For the UR this is `"com.actionml.engines.ur.UREngine"` but varies per Engine type.
 - **mirrorType:** Optional, if mirroring is used, this tell Harness whether to use the hosts raw file system or the distributed Hadoop HDFS. 
 - **mirrorLocation:** Optional, tells the Engine Instance where to log raw JSON events as they come in. This is like a backup but happens in realtime. The Events are logged in a form that can be imported, not as text log files.
 - **modelContainer:** Optional, if the Engine supports model storage, this tells it where to store. For example the CBEngine has a model file in the host file system, which the UREngine does not support this setting since it uses Elasticsearch to store its models. See specific Engine docs for a description.
 - **sharedDBName:** Experimental, do not use unless you understand how the engine lays out data in the Harness supplied DB.

 
# `sparkConf`

This section is optional but needed by Engines that use Spark for distributed computing. See the Engine documents. For instance the UREngine uses Spark to create and update its model, in which case the settings will control the Spark Job.

# `dataset`

This section is optional. One common setting controls the length of time to save streaming data like input events. Each Engine decides how to use this. For instance the UREngine will use the `ttl` as a time limit as which point any timestamped event will be trimmed from the event store.

```
"dataset": {
    "ttl": "90 days"
}
```

The format for the value follow Scala Duration conventions allowing some floating point number followed by "days", "minutes", "seconds", etc. This TTL is applied to the `eventTime` value of the input event. Any Event older than the `ttl` will be trimmed.

**NOTE:** reserved events like `$set` actually change mutable data in the store and so are not kept as Events with `eventTime` their data are not affected by `ttl`. This means that object properties that are `$set` must be `$delete`d or `$unset` otherwise they are never removed from the dataset.

# `algorithm`

The `algorithm` section contains parameters that control the Machine Leaning Algorithm. These are tightly coupled to the Engine type so see the docs for your Engine to set these

# Other Sections

Engines may define their own sections. None are currently known.