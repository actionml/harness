# Input Mirroring

Harness will mirror (log) all raw events with no validation, when configured to do so for a specific Engine instance. This is useful if you wanted to be able to backup/restore all data or are experimenting with changes in engine parameters and wish to recreate the models using past mirrored data.

To accomplish this, you must set up mirroring for the Harness Server. Once the Engine is launched with a mirrored configuration all events sent to `POST /engines/<engine-id>/events` will be mirrored to a location set in `some-engine.json`. **Note** Events will be mirrored until the config setting is changed and so can grow without limit, like unrotated server logs.  

To enable mirroring add the following to the `some-engine.json` for the engine you want to mirror events:

    "mirrorType": "localfs", // optional, turn on a type of mirroring
    "mirrorContainer": "path/to/mirror", // optional, where to mirror input

set these in the global engine params, not in algorithm params as in the "Base Parameters" section above. 

Mirroring is similar to logging. Each new Event is logged to a file before any validation. The format is JSON one event per line. This can be used to backup an Engine Instance or to move data to a new instance.

