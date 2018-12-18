# Harness Installation

This guide shows how to build and install Harness server, Java SDK, and Python SDK examples from source. This is targeted at macOS (BSD Unix based) and Debian/Ubuntu (16.04 or later).

# Requirements

These projects are related and should be built together when installing from source (tarball and jar build TBD):

 1. The Harness Rest-Server (ActionML repo)
 2. The Harness Auth-Server (ActionML repo)
 3. Engines' dependencies (external tarball, apt-get, yum, or brew installs)
 4. Java-sdk (ActionML repo)
 5. Python sdk (included with the Harness Server AML repo)

## Repositories
For Harness 0.3.0+ These are split into 3 GitHub repositories.

 1. [Harness-Rest-Server](https://github.com/actionml/harness): rest-server + Engines + Python CLI + Python SDK
 2. [Harness-Auth-Server](https://github.com/actionml/harness-auth-server): this is quite stable and has had no major changes since 1/2018 and so is not likely to change
 3. The [Harness Java-SDK](https://github.com/actionml/harness-java-sdk) only supports Events and Queries and so is also stable and not very likely to change. The project contains some Java SDK based integration tests but these are being moved to also work in the Harness repo as Python SDK based tests.

For a guide to using IntelliJ for debugging see [Debugging with IntelliJ](debugging_with_intellij.md).

## General Requirements

 - **Java 8:** this should be installed as a dependency of Scala 2.11 but install it if needed, make sure to get the "JDK" version not just the "JRE". Also add your JAVA_HOME to the environment
 - **Scala 2.11:** (optional), the Harness build is done by `sbt`, which will download the correct version of Scala if needed. If you plan to run the Scala REPL for experimenting with Scala, install using `apt-get`, `yum`, or `brew`
 - **Boost 1.55.0:** (only needed for the Contextual Bandit "CBEngine". 1.55.0 or higher is fine. This needed for Vowpal Wabbit/CBEngine runtime.
 - **Git**
 - **MongoDB 3.6 to 4+:** this may require a newer version than in the distro package repos, so check MongoDB docs for installation. These instructions [install Mongo 4.0 on Ubuntu 14.04 thru 18.04](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/)
 - **Spark 2.1.2+**: Used by the Universal Recommender and URNavHintingEngine. Can be run locally in the Harness process via linked in code or setup remotely.
 - **Elasticsearch 5.x - 6+**: Used by the UREngine and URNavHinting.
 - **Other:** Each Engine or component may have its own requirements, see each below.

## Install Requirements on Ubuntu 18.04

 - Create regular user with home and shell. On AWS and other cloud providers this means to login with the power credentials ("ubuntu" user for AWS). Then use this sudoer to create a regular user account.

    ```
    # login as the power user, the sudoer user
    sudo useradd -m -c "ActionML" aml  -s /bin/bash
    sudo gpasswd -a aml sudo
    sudo nano /etc/sudoers
    # change 
   ```

 - Add key to the `aml` user's `authorized_keys`

    ```
    sudo su aml
    cd /home/aml
    ssh-keygen # hit CR for all questions
    nano .ssh/authorized_keys # add your public key to the file
    chmod 600 .ssh/authorized_keys
    ```
    
    Now you can login as "aml@some-ip-address" using your public key. There are better ways to do this for groups using the ssh agent but this is left to the user to read about.

 - Get Java JDK using Open-JDK from the apt repos.

    ```
    sudo apt update
    sudo apt install openjdk-8-jdk git python3
    ```
    
 - Mongo needs a newer version than in is the default 18.04 repos so add the correct repo and install from there.

    ```
    # your installation will look SOMETHING like this
    # but read the Mongo instructions for specifc command lines
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 9DA31620334BD75D9DCB49F368818C72E52529D4
    echo "deb [ arch=amd64 ] https://repo.mongodb.org/apt/ubuntu bionic/mongodb-org/4.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-4.0.list
    sudo apt-get update
    sudo apt-get install -y mongodb-org
    echo "mongodb-org hold" | sudo dpkg --set-selections
    echo "mongodb-org-server hold" | sudo dpkg --set-selections
    echo "mongodb-org-shell hold" | sudo dpkg --set-selections
    echo "mongodb-org-mongos hold" | sudo dpkg --set-selections
    echo "mongodb-org-tools hold" | sudo dpkg --set-selections
    sudo service mongod start
    ``` 
    
 - sbt, scala's "simple build tool". Sbt will pull in the correct Scala version needed so unless you want to use scala directly you will not need to install it.

    ```
    echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
    sudo apt-get update
    sudo apt-get install sbt
    ```
      
## The Contextual Bandit (optional)
 
The Contextual Bandit is a sample Engine, which can be ignored if not being used.

The Contextual Bandit is a Kappa style online learning Engine based on the Vowpal Wabbit ML compute engine. To use this Engine (ignore if you are not using it), will require that you first build and install VW:

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

# Harness Build from Source (release/0.4.0-snapshot branch)

The AuthServer must be built first and jars put in the local sbt cache. Then the Harness Server with build successfully. This is needed even if you are not using the AuthServer.

## Harness Auth-Server

Get and build from source. The Auth-Server is pretty stable and has not had any changes since v0.1.0. So it is now released into the master branch of its repo.

```
git clone https://github.com/actionml/harness-auth-server.git harness-auth-server
cd harness-auth-server
./make-auth-server-distribution.sh
tar xvf AuthServer-0.3.x.ab.tar.gz # use the version in the tarball name
```

This creates the AuthServer but it is not needed for local testing when auth and TLS are not being used. It must be built to create jars in the local `/.ivy2` cache. 

**NOTE**: After building you MUST populate the cache, in order to build Harness:

```
sbt harnessAuthCommon/publish-local
```

## Harness (release/0.4.0-snapshot)

Get and build source:
 
```
git clone -b develop https://github.com/actionml/harness.git harness
cd harness/rest-server
git checkout -b release/0.4.0-snapshot
git pull origin release/0.4.0-snapshot
./make-harness-distribution.sh
tar xvf Harness-0.4.0-SNAPSHOT.tar.gz # use the version number in the tarball name, if different
nano Harness-0.4.0-SNAPSHOT/bin/harness-env # config the env for Harness
```

The default config in `harness-env` is usually sufficient for testing locally. You can also copy the file from earlier builds, little has changed in it.

Add the Harness CLI (located the `bin/` directory of the distribution tarball) to your PATH by including something like the following in `~/.profile` or wherever you OS requires it.
    
```
export PATH=$PATH:/home/<your-user-name>/harness/rest-server/Harness-0.4.0-SNAPSHOT/bin/
# substitute your path to the distribution's "bin" directory
# the dir should contain the .../bin/main file and other CLI scripts
```
    
Then source the `.profile` with 
    
```
. ~/.profile
```

## Python SDK

The Python SDK is needed for virtually all Harness CLI since Harness only responds to the REST API and therefore the CLI does this through the Python SDK.

**Requirements**

 - **Python3**: This can usually be installed from the distribution's repos using `apt-get`, `brew`, or `yum`.
 - **Pip3**: This is required to run the setup script that installs the Harness Python SDK and can be installed through your platform's distribution repos.
 - **Harness Python SDK** for local use:

    ```
    cd harness/python-sdk # wherever the source is installed
    python3 setup.py install # you may need to add "sudo" to this
    ```

You are now ready to launch and run the Harness CLI but you will need more services for included engines so read on.

# Elasticsearch 5.x or 6.x+

The Universal Recommender (and variants), requires Elasticsearch to store the model and to run special queries not implemented by many other DBS. These special "similarity" queries are actually part of the UR's CCO algorithm.

Elasticsearch can be installed from supported Debian apt-get repos, Fedora yum repos, or macOS brew repos. All will install 6+.

## macOS

    $ brew update
    $ brew install elasticsearch

## Ubuntu

The repo to use should match the version of Ubuntu. For Ubuntu 16.06 LTS your have already installed Java 8 so see the rest here: [Elasticsearch on Ubuntu](https://www.howtoforge.com/tutorial/how-to-install-elastic-stack-on-ubuntu-16-04/#step-install-and-configure-elasticsearch). You only need to perform step #2.

Your instructions will look SOMETHING like this:

```
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo apt-key add -
sudo echo "deb https://artifacts.elastic.co/packages/6.x/apt stable main" >> /etc/apt/sources.list.d/elastic.list
sudo apt-get update
sudo apt-get install elasticsearch
sudo systemctl start elasticsearch.service
```

# Launching Harness  

 - **Setup Harness config:** To configure Harness for localhost connections you should not need to change the default configuration in `harness/Harness-0.4.0-SNAPSHOT/bin/harness-env`. Look there to see examples for changing global Harness config.
 - **Set the `path`** in your env to include the `bin` directory of both Harness and optionally the Harness Auth server: 

    ```
    export PATH=/path/to/harness/bin:/path/to/harness-auth/bin:$PATH`
    ```
    
 - **Start Other Services:** Harness and its Engines should have their dependent services started on boot or start them before Harness.
 
 - **Start Harness:**

    ```
    harness start # you will get a status message printed
    harness status engines # will list all active engines
    ```

    See [Commands](commands.md) for a description of the Harness CLI.
    
# Advanced Settings

See [Advanced Settings](advanced_settings.md) for allowing external connections, using Auth, TLS/SSL, and other settings.

