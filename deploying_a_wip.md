# Deploying a WIP Harness Server

This describes the process for deploying Harness from some dev branch, not yet released. This is necessary sometimes for staging the server in order to write integration code for the client application before a release can be tested or finalized.

This describes extra steps not covered in the [install](install.md) doc.

# Prepare the WIP branch

 1. Build and test (see test section) Harness pre-0.3.0 release on a dev machine from whatever branch is the work in progress. **Make sure the version number of harness uniquely describes the WIP version being deployed.** The new version should be `Harness-0.3.0-feature-ur-1` to include the WIP branch and the deployment number. This will cause `harness status` to report the number and give the `tar.gz` a unique name. 
 2. Push the branch to GitHub

# Prepare the Staging Server

Backup the state of the serve so it can be restored in case the WIP branch has problems.

 1. Backup the state of the DB with `mongodump` to [backup all contents](https://docs.mongodb.com/manual/tutorial/backup-and-restore-tools/). Put it in some standard location and make sure it is timestamped. 
 2. `harness stop` the old running deployment
 3. Once the dump success is verified wipe the entire contents of the DB with the following MongoDB shell commands:
    - `mongo` from Bash to enter the shell
    - `show dbs` to list all dbs
    - `use db1` use the first in the list
    - `db.dropDatabase()` to drop the used db
    - repeat for all. It is especially important to drop `harness-meta-store`
 4. The previous harness server deployment came from a tar.gz located in harness/rest-server so leave it there. It will have a version name tied to the last deployment and have a timestamp of the last build date.
 5. `git pull feature/wip-branch` Whatever branch is being deployed
 6. `cd harness/rest-server`
 7. `make-harness-distribution.sh` to build the `tar.gz` 
 7. `tar xvf Harness-0.3.0-feature-ur-1` to unzip the build in the harness/rest-server location where is was built.
 8. Change the `/usr/local/harness` symlink to point to the `bin` directory in the unzipped build directory.
 9. `harness start` starts the new deployment

If you have trouble with the CLI you may need to reinstall the Python SDK with the following process:

 1. `cd harness/python-sdk`
 2. `python3 setup.py install` Which may need `sudo` depending on server setup.

# Smoke-test Harness with Released Engines

Run whatever WIP tests are available to make sure the WIP features are working.

 1. run the integration test from the Java SDK repo, built elsewhere on the server. This will fairly well test the last released code and is therefore a minimal regression test.
 2. run any new WIP tests needed, even if they require a hand done process. For instance the **URNavHintingEngine Smoke test** below.
 3. `harness stop` stop the server
 4. Move or modify the build's config. This is done by copying the previous version of `bin/harness-env.sh` from the previously deployed server to the new server build location.
 5. Edit `harness-env.sh` with any newly required config.
 6. `harness start` the server. This should put TLS/SSL and Auth into the same state as the previous deployment and print out the version number expected.
 7. Test the CLI is running, which should test TLS and Auth
    - `harness status engines` There should be no engines    

# URNavHinting Smoke test 

Before TLS and Auth are enabled but while the newly deployed WIP server is running, perform a simple smoke test of the server.

 1. `cd harness/rest-server`
 2. `harness status engines` there should be none.
 3. `harness add example/data/ur_nav_hinting.json` This should add the engine.
 4. `harness status engines` This should report the new engine
 5. `harness status engines ur_nav_hinting` should give some engine config information
 6. `cd example`
 7. `python3 ur_nav_hinting_import_handmade.py` this should echo events input to the new engine
 8. `harness delete ur_nav_hinting` this will remove the engine and data just imported.
 9. `harness add example/data/ur_nav_hinting.json` to leave a running version for external integration. 
