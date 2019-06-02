# Harness JSON
***Harness 0.5.0+***

Harness uses JSON for all requests and response bodies to its REST API. It also enforces certain conventions:

 - **Resource-ids**: For identification all resources have URI type ids. The most common usage is for Engine Instance Ids, defined in instance configuration JSON. Engine Instance Ids are defined by the user and so should be unique and be URI encoded. For instance a lower case snake_case string is valid.
 - **Strings**: any string used by Harness should be UTF-8 encoded.
 - **Dates** are supported as Strings of ISO 8601 defined by Java's [`ISO_DATE_FORMAT`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_DATE_TIME). This includes most common formats with time zones, and what is called the "Zulu" format.
 - **Validation**: The values embedded in JSON are validated by the target entity. If the REST API targets Harness (as for admin commands) then the specification is in the Harness documentation. If the target entity is an Engine, then each Engine defines and performs its own validation so the spec should be in the Engine documentation.

# Engine Config Extended JSON

Every Engine Instance in Harness is configured via JSON.  Another way to say this is that in Harness an Engine + JSON Config = an Engine Instance. For example the CLI `harnessctl add /path/to/some-engine.json` will send a JSON configuration object to Harness with all parameters needed to run the Instance including both environment and algorithm parameters.

Borrowing from Node.js, Harness supports reading values from the Harness server's `env`. For example, if `some-engine.json` says:

```
"master": "system.env.SPARK_MASTER"
```

then the value of the server `env` var `SPARK_MASTER` will be passed in as the value for `master`. In this example `master` is used to run Spark Jobs so extended JSON allows the `env` to control configuration and the Engine Instance can be defined in a deployment independent fashion.

This is particularly important when using Docker Containers or Kubernetes.

# Version

Supported in Harness 0.5.0+