# Harness JSON
***Harness 0.5.0+***

Every Engine in Harness has a way to configure its parameters via a JSON file. Another way to say this is that in Harness an Engine + JSON Config = an Engine Instance.

Using JSON to configure an Engine Instance while supporting containerized deployment, requires that arbitrary parameters be configurable via environment variables.

Borrowing from Node.js we pass the names of environment variables in the JSON.

For example, if the config JSON says:

```
"es.nodes": "system.env.ES_NODES"
```

then the value of the env var `ES_NODES` will be passed in as the value for `es.nodes`. This allows the env to control parameters in any way the system need and can be particularly useful with containers.

# Version

Supported in Harness 0.5.0+