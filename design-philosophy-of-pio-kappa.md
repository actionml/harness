# Features of pio-kappa

The pio-kappa design philosophy and target audience is the Engineer who want to run some collections of engines but does not want to implement them. Therefor design trade-offs have been taken to favor flexibility and the ability to create turnkey engines over making Template development easier. That said, all Template development must be easy to do, understand, and debug. We expect Template developers to be moderately skilled in Scala development. We expect users of Templates to be skilled in using REST APIs in some form, and we supply client Python of Java SDKs to aid them.

pio-kappa has taken many design patterns from Apache PredictionIO but aims to add some new features and fix some weaknesses and so is an almost complete re-design. The project aims to retain data compatibility with Apache PredictionIO in terms of input. The pio-kappa Templates have some things in common and will be relatively simple to convert but do not aim for API level compatibility.

Changes in pio-kappa architecture will support:

 - **Kappa and Lambda training**: online streaming learners as well as periodic background / batch training. Apache PIO only supports Lambda.
 - **Robust Data Validation**: input for templates is defined in format and meaning by the Template in both Apache PIO and pio-kappa. But pio-kappa allows the Template to validate every input and report errors. 
 - **Input Stream**: since data is validated by the Template it may also affect algorithm state so while Apache PIO treats the input as an immutable un-ending stream, pio-kappa templates may not. They are free to change the model in real-time, which is necessary to support online learning and often desirable for lambda learning. Therefor pio-kappa exports are for backup only, they cannot be replayed into some other Engine. For this reason the input stream of events can be copied to HDFS for later replay. For those familiar with Apache PIO this means we have split the notion of input and engine state.
 - **API**: The API of record for **all** of pio-kappa is REST. Even the CLI is implemented through the REST API.
 - **SDKs**: pio-kappa has a Python and a Java SDK (Scala may use the Java SDK as-is) The SDKs support input and queries only. Other admin CLI invokes admin REST APIs.
 - **CLI**: The shell CLI is implemented as Python scripts that invoke the REST API, in some cases through the Python SDK.
 - **Security**: HTTPS is supported for the REST interface, including the admin part. Server to Server OAuth2-based authentication is also supported. This allows a remote CLI or web interface to access all functions of pio-kappa securely over the internet.
 - **Multi-tenancy**: fundamental integral multi-tenancy of all the REST API is supported through the use of resource-ids. Input can be sent to any number of engines by directing events to the correct resource (see the REST API for details). This includes input, queries, and admin functions.  Here an "engine" can be seen as an instance of a Template with a particular algorithm, dataset, and parameters.

# Templates for pio-kappa

Template + parameters + data = Engine. An Engine in a Template instance that knows where to put data, and how to fire up an Algorithm to process this data. A Template is a collection of Classes that agree to supply a very simple API contract. The primary components are:

 - **Engine**: The Engine class is the "controller" in the MVC use of the term. It takes all input, parses and validates it, then understands where to send it and how to report errors. It also fields queries processing them in much the same way. It creates Datasets and Algorithms and uses a Store.
 - **Algorithm**: The Algorithm understands where data is and implements the Machine Learning part of the system. It converts Datasets into Models and implements the queries. The Engine controls initialization of the Algorithm with parameters and triggers training and queries. For kappa style learning the Engine triggers training on every input so the Algorithm may spin up Akka based Actors to perform continuous training. For Lambda the training may be periodic and performed less often and may use a compute engine like Spark or TensorFlow to perform this periodic triaining. There is nothing in the pio-kappa that enforce the methodology or compute platform used.
 - **Dataset**: A Dataset may be comprised of event streams and mutable data structures in any combination. The state of these is always stored outside of pio-kappa in a separate scalable Store.
 - **Store**: The Store is a very lightweight way of accessing some external persistence. The knowledge of how to use the store is contained in anything that uses is. The Store does not attempt to virtualize access to a DB for example. In some cases the Store may be a file system. Again no attempt to provide a virtualized API for the physical store is made and for the Administrative portions of pio-kappa the Store is MongoDB.
 - **Administrator**: Each of the above Classes is extended by a specific Template and the Administrator handles CRUD operations on the Template instances called Engines, which in turn manage there constituent Algorithms and Datasets. The Administrator manages the state of the system through persistence in a scalable DB (default is MongoDB) as well as attaching the Engine to the input and query REST endpoints corresponding to the assigned resource-id for the Template instance.
 - **Parameters**: are JSON descriptions of Template initialization information. They may contain information for the Administrator, Engine, Algorithm, and Dataset. The first key part of information is the Engine ID, which is used as the resource-id in the REST API. The admin CLI may take the JSON from some `engine.json` file when creating a new engine or re-initializing one but internally the Parameters are stored persistently and only changed when the admin API is triggered.

From the point of view of a user of pio-kappa (leaving out installation for now) from the very beginning of operation one would expect to perform the following steps:

 1. Start pio-kappa, this will automatically startup any previously created Engines, making them ready for input and queries and/or training.
 2. In the case of creating a new engine, ask the Administrator to create an Engine given a JSON string of parameters and a Jar containing code to run the Engine. The JSON will contain a string which is the classname of an Engine Factory object, which is able to create an Engine that can consume the rest of the JSON.
 3. pio-kappa now has a new set of endpoints for the new Engine attached to the resource-id provided in the JSON Parameters. This Engine may share resources with other Engines in the system but typically does not know about them and is independent.
 4. At some point an Engine may need to be removed, which removes all persistent data the Engine had from input or as a result of Algorithms creating Models. In this case ask the Administrator to destroy an the particular Engine.
 5. If pio-kappa is to be shutdown, it may be killed by pid or asked to shut itself down.

 ## Engine, Algorithm, Dataset: Class Contract
 
Classes will have constrictors that do very little and nothing with side-effects. Most work is done by `init` and `destroy`. `init` initializes  including parsing and validating Parameters, perhaps initialization or prep for initialization of a compute engine like Spark or VW. `init` and `destroy` affect persisted sate. `start` and `stop` assume an initialized object and start or stop work in the form of input, query, or training.
 
  - **init**: take JSON string as input, which can be parsed and validated in a manner specific to the template. This same string is passed to all classes which are in charge of pulling out what they understand **and nothing else**. This may have side-effects like creating or retrieving DB objects. The extending classes must implement `init( p: String): Validated[ValidateError, Boolean]` The returned `Validated` may include an error specific to the Template (some init error) or it may be a failure to parse the JSON (malformed parameters JSON). 
  - **destroy**: this tries to remove all evidence that the Engine and it's components ever existed. It removes all state evidence created by the object. `destroy(): Validated[ValidateError, Boolean]` may return an error if some part of state removal fails.
  - **start**: given an initialized object begin performing work and persisting state. After start the engine can begin input, query, and training work.
  - **stop**: suspend work cleanly but do not destroy persistent state. This should be called before `destroy` but should not assume that `destroy` will be called at all. It may be useful for start and stop Engines on demand, independent of init and destroy