# Harness Installation

This guide shows how to build and install Harness server, Java SDK, and Python SDK examples from source. Much of this is targeted at macOS (BSD Unix based) and Debian/Ubuntu (16.04 or later).

There are 4 projects in the GitHub repo:

 1. The Harness server 
 2. java-sdk 
 3. java-sdk examples 
 4. python sdk

For a guide to using IntelliJ for debugging see [Debugging with IntelliJ](debugging_with_intellij.md) as well as set them up for debugging with IntelliJ. Much of this is targeted at Debian/Ubuntu with side notes for macOS (BSD based).

When using the source from GitHub follow these instructions to build and deploy.

**General Requirements:**

 - Scala 2.11, install using `apt-get`, `yum`, or `brew`
 - Java 8, this should be installed as a dependency of Scala 2.11 but install it if needed, make sure to get the "JDK" version not just the "JRE". Also add your JAVA_HOME to thee environment
 - Boost 1.55.0 or higher is fine. This is only for Vowpal Wabbit
 - Git

# The Contextual Bandit
 
Included in the project is a sample Kappa style Template for a Contextual Bandit based on the Vowpal Wabbit ML compute engine. To build Harness will require that you first build and install VW:

 - For Ubuntu get VW dependencies:

    ```
    sudo apt-get install libboost-program-options-dev zlib1g-dev
    sudo apt-get install clang
    sudo apt-get install maven
    ```

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
    
    Build VW and install it in the right place.
    
    ```
    git clone https://github.com/pferrel/vowpal_wabbit.git vw
    cd vw
    git checkout 1aa674a136c0c793dae33b12debcc66c0efbc884
    make java
    cd java
    cp target/libvw_jni.so /usr/lib/jni # take from Scala above
    mvn install
    ```
    
    This builds and installs the dynamic load lib for VW in a place that the Java JNI wrapper can find it. For macOS, follow the same steps but the copy-to location will be different and VW will be in `target/libvw_jni.dylib` 

    You are now ready to build the rest-server portion of Harness. This includes the CLI and services the Harness REST API.

# Harness Rest-Server

 - Get and build source:
 
    ```
    git clone https://github.com/actionml/harness.git
    cd harness/rest-server
    ./make-distribution
    tar xvf Harness-0.1.0-SNAPSHOT
    nano Harness-0.1.0-SNAPSHOT/bin/harness-env # config the env for Harness
    ```

    Add the path to the Harness CLI to your PATH by including something like the following in `~/.profile`
    
    ```
    export PATH=$PATH:/home/aml/harness/rest-server/Harness-0.1.0-SNAPSHOT/bin/
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
harness add -c /path/to/some/engine.json # get a success response
```

You now have an empty Contextual Bandit Engine at the resource-id referenced in the engine.json.

To send events and make queries see "examples" directories in `java-sdk` and `python-sdk`.

When you are done playing around remove the Engine since is may be set up to mirror input and therefor taking up disk space with events.

```
harness delete test_resource # or whatever the engine's resource-id is
# if you want to shutdown the server
harness stop
```

**Note**: if you have put the `harness` script on your path commands can be executed from anywhere

