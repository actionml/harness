# Upgrading from PIO+UR to Harness+UR

If you are primarily using the Universal Recommender with PIO you should find upgrading fairly easy. Outline:

 1. With PIO running export your data from the UR
 2. start Harness
 3. add a UR engine instance and parameters
 4. import your data
 5. train the UR

After step 3 you are ready for input and after 5 you are ready for queries. To send Input and Queries will require using Harness REST in your client but the UR is ready. 

# Backup Your Data

With PredictionIO running, export all your data. This is done with:

 - `pio app list` to find your `appid`
 - `pio export --appid <id-from-step-1> --output </path/to/export/location>` export all data

This gives you a snapshot of your PIO hosted data.

# Install and Start Harness

Follow instructions to [Install Harness](install.md). This can be done by hand on your host or by using Docker containers. Start Harness, it already contains the UR and other Engines.

# Configure the UR

Harness is fully multi-tenant so every reference to an Engine Instance uses an engine-id. This is created by setting it in a config JSON file with other parameters. The format is similar to the PIO engine.json for the UR with some changes:

 - `indicators` instead of `eventNames`
 - `rules` instead of `fields`
 - the Harness version also has a slightly different structure. 

Create a JSON file for the UR substituting your values from the PIO version of `engine.json` to fit the description in [UR Configuration](ur_configuration.md)

# Start Your UR Instance

Harness can run many instances of Engines like the UR. Once you have installed Harness and the Harness-cli you can add one that will work like your PIO UR:

 - `harness-cli status` checks only harness running status, so hand check that MongoDB and Elasticsearch are also running
 - `harness add </path/to/ur/config/json>` This will create an Engine Instance for the UR with the `engineId` named in the JSON file
 - `harness-cli status engines <ur-instance-id>` see the params you set for this instance and verify everything is running.
 - `harness-cli import <ur-instance-id> </path/to/export/location>` import from data exported by PIO + UR.
 - `harness-cli train <ur-instance-id>` when completed, this will allow you to query the UR using REST or the new Harness SKD (Java, Scala, Python). 

# Input and Queries

At this point Harness and the UR are ready for live input and queries. To make them you need to use a couple REST endpoints. Below `<harness-host>` is your IP address for the instance of Harness you started.

 - `POST http://<harness-host>:9090/engines/<ur-engine-instance>/events` where the request is a UR input JSON packet that is identical to PredictionIO, only the URI is different.
 - `POST http://<harness-host>:9090/engines/<ur-engine-instance>/queries` where the request is a UR query JSON packet identical to PIO, only the URI is different.

Alternatively you can use one of the Harness SDKs to do queries and input.

For more details see [Input and Query](howto_input_and_query.md)

