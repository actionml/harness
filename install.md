# Harness Installation

This guide shows how to build and install Harness server, Java SDK, and Python SDK examples from source. Much of this is targeted at macOS (BSD Unix based) and Debian/Ubuntu (16.04 or later).

# Requirements

These projects are related and should be built together when installing from source (tarball and jar build TBD):

 1. The Harness server
 2. Engines' dependencies
 3. java-sdk 
 4. python sdk

## Repositories
For Harness 0.3.0+ These are split into 3 GitHub repositories. `git clone` these into separate direcories.

 1. [Harness](https://github.com/actionml/harness/tree/release/0.3.0-SNAPSHOT): rest-server + Engines + Python CLI + Python SDK
 2. [Harness-Auth-Server](https://github.com/actionml/harness-auth-server): this is quite stable and has had no major changes for since 1/2018 and so is less subject to change
 3. The [Harness-Java-SDK](https://github.com/actionml/harness-java-sdk/tree/release/0.3.0-SNAPSHOT) only supports Events and Queries and so is also stable and not very likely to change

For a guide to using IntelliJ for debugging see [Debugging with IntelliJ](debugging_with_intellij.md). Much of this is targeted at Debian/Ubuntu with side notes for macOS (BSD Unix based).

## General Requirements

 - Java 8, this should be installed as a dependency of Scala 2.11 but install it if needed, make sure to get the "JDK" version not just the "JRE". Also add your JAVA_HOME to the environment
 - Scala 2.11, install using `apt-get`, `yum`, or `brew`
 - Boost 1.55.0 or higher is fine. This is only for Vowpal Wabbit
 - Git
 - MongoDB 3.6+ or 4.x, this may require a newer version than in the distro package repos, so check MongoDB docs for installation. For example, these instructions [install Mongo 3.4 on Ubuntu](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/)

## The Contextual Bandit
 
Included in the project is a sample Kappa style Template for a Contextual Bandit based on the Vowpal Wabbit ML compute engine. To build Harness will require that you first build and install VW:

**For macOS** get dependencies:

 - `brew install boost`
 - `brew install maven`
 - `brew install clang`


**For Ubuntu 16.04+ or Debian** get dependencies:

 - `sudo apt-get install libboost-program-options-dev zlib1g-dev clang maven`

**Get, Build, and Install VW binary lib and JNI wrapper**

 - Get Vowpal Wabbit, the compute engine used for the Contextual Bandit Harness Template. This is a requirement for building Harness since this Template is used in integration tests and to run the Contextual bandit.
    
    To discover the place to put the VW dynamic load lib launch Scala and do:
    
    ```
    aml@ip-x-x-x-x:~$ scala
    Welcome to Scala version 2.11.4 (OpenJDK 64-Bit Server VM, Java 1.8.0_111).
    Type in expressions to have them evaluated.
    Type :help for more information.
    
    scala> System.getProperty("java.library.path")
    res0: String = /usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib
    ```
    
    For masOS the location may be something like: 
    
    ```
    res0: String = /Users/aml/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:.
    ```
    
    We will use the JNI lib location `/usr/lib/jni` or its equivalent on your system
    
    Build VW and install it in the right place. First find the path above using the Scala REPL shell, then make sure the directory exists, you may need to create it, then copy the binary dynamic lib to the jni location.
    
    ```
    git clone https://github.com/pferrel/vowpal_wabbit.git vw
    cd vw
    git checkout 10bd09ab06f59291e04ad7805e88fd3e693b7159
    make java # this builds the vw dynamic lib and the Java JNI wrapper
    cd java
    # for linux
    cp target/libvw_jni.so /usr/lib/jni/ # take from Scala above
    # for macOS
    cp target/libvw_jni.dylib /Users/<yourname>/Library/Java/Extensions/
    mvn test # we will get the Java wrapper from maven when
    # we build Harness so this is only to test that VW is set
    # up to be used by the JVM
    ```
    
    This builds and installs the dynamic load lib for VW in a place that the Java JNI wrapper can find it. You are now ready to build the Harness Server. This includes the CLI and services the Harness REST API.

# Full Harness 0.3.0-SNAPSHOT Build

The AuthServer must be built first and jars put in the local sbt cache. Then the Harness Server with build successfully. This is needed even if you are not using the AuthServer.

## AuthServer-0.3.0-SNAPSHOT

Get and build from source

```
git clone -b release/0.3.0-SNAPSHOT https://github.com/actionml/harness-auth-server.git harness-auth-server
cd harness-auth-server
./make-auth-server-distribution.sh
tar xvf AuthServer-0.3.0-SNAPSHOT.tar.gz
```

This creates the AuthServer but it is not needed for local testing.

If all you need is the jars for imported classes to be cached for SBT when building the Harness server. Run this command:

```
sbt harnessAuthCommon/publish-local
```

## Harness-0.3.0-SNAPSHOT

Get and build source:
 
```
git clone -b release/0.3.0-SNAPSHOT https://github.com/actionml/harness.git harness
cd harness/rest-server
./make-distribution
tar xvf Harness-0.3.0-SNAPSHOT
nano Harness-0.0.3.0-SNAPSHOT/bin/harness-env # config the env for Harness
```

The default config in `harness-env` is usually sufficient for testing locally.

Add the Harness CLI (the `bin/` directory in the distribution) to your PATH by including something like the following in `~/.profile` or wherever you OS requires it.
    
```
export PATH=$PATH:/home/<your-user-name>/harness/rest-server/Harness-0.3.0-SNAPSHOT/bin/
# substitute your path to the distribution's "bin" directory
# it should have the .../bin/main file and other CLI scripts
```
    
Then source the `.profile` with 
    
```
. ~/.profile
```

## Python SDK 0.3.0a0

The Python SDK is needed for virtually all Harness CLI since Harness only responds to the REST API and therefore the CLI does this through the Python SDK.

The Python SDK requires python3 to be installed and all use of python will invoke `python3` This means that no code should assume `python` executes `python3`. In other words no symlink of `python -> python3` exists.

```
cd harness/python-sdk
python3 setup.py install
```

You are now ready to launch and run Harness with the included Engines

# Elasticsearch 5.x or 6.x

Some newer engines require Elasticsearch. Anything based on the Universal Recommender, for instance, uses Elasticsearch to store the model and to run special queries not allowed in other DBS. These special "similarity" queries are actually part of the algorithm so Elasticsearch is a requirement (although any engine based on Lucene could be used, if you write your own client to perform the same operations).

Elasticsearch can be installed from supported Debian apt-get repos, Fedora yum repos, or macOS brew repos. All will install 5.x or newer.

## macOS High Sierra

    $ brew update
    $ brew install elasticsearch

## Ubuntu

The repo to use should match the version of Ubuntu. For Ubuntu 16.06 LTS your have already installed Java 8 so see the rest here: [Elasticsearch on Ubuntu](https://www.howtoforge.com/tutorial/how-to-install-elastic-stack-on-ubuntu-16-04/#step-install-and-configure-elasticsearch). You only need to perform step #2.

## Docker Elasticsearch

If you wish to use Docker containers for ES, just be sure to supply the host a port in any Engine's JSON config (not supported for 0.3.0-RC1)

# Launching Harness  

To configure Harness for localhost connections you should not need to change the configuration in `harness/Harness-0.3.0-SNAPSHOT/bin/harness-env`. Look there to see examples for changing port numbers and if you want to connect to Harness from other hosts have it listen to `0.0.0.0` instead of `localhost`, which is default.

```
harness start # you will get a status message printed
harness add <path-to-engine's-json-file> # get a success response
harness status <engine-id-from-json-file>
```

You now have an empty Engine of the type, `engineId`, and params defined in the Engines config JSON file.

To send events and make queries see "examples" directories in `java-sdk` and `python-sdk`.

When you are done playing around delete the Engine to clear out the DB and disk used by the Engine.

```
harness delete !!your engineId!! # use the engine's engineId
# if you want to shutdown the server
harness stop
```

**Note**: if you have put the `harness` script on your path commands can be executed from anywhere

## Simple CLI Integration Test

A simple CLI integration test is included

```
cd harness/rest-server/Harness-0.3.0-SNAPSHOT/
./harness-cli-test
```

# Running the Integration Test

```
cd harness/java-sdk
./integration-test.sh
```

You may see errors for deleting a non-existent resource or stopping harness when it is not started and this is normal but will not stop the script. If the diff printed at the end has no "important differences" then the test passes.

# Advanced Settings

See [Advanced Settings](advanced_settings.md) for allowing external connections, using Auth, and TLS/SSL.

