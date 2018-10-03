# Running the Harness Integration Test

## PVR and Non-personlized Nav-Hinting

The test on a completely clean source install. Get to the point where you can run the Python commands, especially `harness start`. Note that building Harness requires you build the Auth server on the same machine with `sbt publishlocal` to populate the `~/.ivy2` cache with jars used to build Harness.

If harness is running with no TLS and no Authentication required there is a minimal "smoke test". Follow these steps:

 - Pull the harness-java-sdk repo into a new location with `git clone ...`
 - cd into the local repo
 - make sure you are on the master branch
 - `mvn clean install` This will put needed Harness Java SDK binaries in the local `~/.m2` cache for building the test code. They are not needed to run or build Harness.
 - With Harness running you can now start the integration test
 - `cd harness-java-sdk`
 - `./integration-test.sh`
 
## URNavHinting

This will be merged with the single integration test but is currently run from a different place and uses the Python SDK instead of the Java SDK.

After running the PVR integration test with Harness running:

 - `cd harness/rest-server/examples`
 - `./urnh-integration-test.sh`