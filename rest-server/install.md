### Installing Rest Server from Source Code

```bash
$ wget https://github.com/actionml/harness/archive/develop.zip
$ unzip develop.zip
$ cd ./harness-develop/rest-server
$ ./make-distribution.sh
```

You should see something like the following when it finishes building successfully.

```bash
Building binary distribution for ActionML Harness 0.1.0-SNAPSHOT...
...
ActionML binary distribution created at ActionML-0.1.0-SNAPSHOT.tar.gz
```

Extract the binary distribution you have just built.

```bash
$ tar zxvf Harness-0.1.0-SNAPSHOT.tar.gz
```

Install ActionML Python-SDK

```bash
$ pip install actionml
```
May be for mac needed run

```bash
brew link --overwrite python
```

Start ActionML Rest-Server

```bash
$ cd ./bin
$ ./harness start
```

You should see something like the following when it finishes building successfully.

```bash
$ Starting PIO Kappa Event Server...
```

Check ActionML Rest-Server
```bash
$ ./harness status
```

You should see something like the following when it finishes building successfully.

```bash
$ Connect to ActionML Rest Server [http://localhost:9090] is OK
OR
$ Error connecting to ActionML Rest Server [http://localhost:9090] [Errno 111] Connection refused
```

Add an engine

```bash
./harness add -c path/to/engine.json
```

Stop ActionML Rest-Server

```bash
$ ./harness stop
```

You should see something like the following when it finishes building successfully.

```bash
$ Stopping Harness server... 
```

Restarting the Harness server should restart all of the active engines in the state they were in when last running.
