# Harness Installation

This guide shows how to build and install Harness server, Java SDK, and Python SDK examples from source. Much of this is targeted at macOS (BSD Unix based) and Debian/Ubuntu (16.04 or later).

There are 4 projects in the GitHub repo:

 1. The Harness server 
 2. java-sdk 
 3. java-sdk examples 
 4. python sdk

For a guide to using IntelliJ for debugging see [Debugging with IntelliJ](debugging_with_intellij.md). Much of this is targeted at Debian/Ubuntu with side notes for macOS (BSD Unix based).

When using the source from GitHub follow these instructions to build and deploy.

**General Requirements:**

 - Scala 2.11, install using `apt-get`, `yum`, or `brew`
 - Java 8, this should be installed as a dependency of Scala 2.11 but install it if needed, make sure to get the "JDK" version not just the "JRE". Also add your JAVA_HOME to the environment
 - Boost 1.55.0 or higher is fine. This is only for Vowpal Wabbit
 - Git
 - MongoDB 3.x, this may require a newer version than in the distro package repos, so check MongoDB docs for installation. For example, these instructions [install Mongo 3.4 on Ubuntu](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/)

# The Contextual Bandit
 
Included in the project is a sample Kappa style Template for a Contextual Bandit based on the Vowpal Wabbit ML compute engine. To build Harness will require that you first build and install VW:

**For macOS** get dependencies:

 - `sudo brew install boost maven clang`


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

# Harness Server

 - Get and build source:
 
    ```
    git clone https://github.com/actionml/harness.git
    cd harness/rest-server
    ./make-distribution
    tar xvf Harness-0.1.1-RC3
    nano Harness-0.1.1-RC3/bin/harness-env # config the env for Harness
    ```

    Add the path to the Harness CLI to your PATH by including something like the following in `~/.profile` or wherever you OS requires it.
    
    ```
    export PATH=$PATH:/home/aml/harness/rest-server/Harness-0.1.1-RC3/bin/
    # substitute your path to the distribution's "bin" directory
    # it should have the .../bin/main file
    ```
    
    Then source the `.profile` with 
    
    ```
    . ~/.profile
    ```

    You are now ready to launch and run Harness with the included Contextual Bandit.

# Launching Harness  

To configure Harness for localhost connections you should not need to change the configuration in `harness/Harness-0.1.0-SNAPSHOT/bin/harness-env`. Look there to see examples for changing port numbers and if you want to connect to Harness from other hosts have it listen to `0.0.0.0` instead of `localhost`.

```
harness start # you will get a status message printed
harness add !!path to the engine's JSON params file!! # get a success response
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

# Running the Integration Test

```
cd harness/java-sdk
./integration-test.sh
```

You may see errors for deleting a non-existent resource or stopping harness when it is not started and this is normal but will not stop the script. If the diff printed at the end has no "important differences" then the test passes.

# Advanced Settings

See [Advanced Settings](advanced_settings.md) for allowing external connections, using Auth, and TLS/SSL.

