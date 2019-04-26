# Continuous Integration for Harness

Container and circleci files for the CI pipeline.

## circleci/config.yml

CircleCI is used and triggered by any push to the `develop` or `master` branch of Harness. This both tests the build and publishes a Docker image.

circleci deployment tags harness image depending on branch (develop and master). Develop is the current work in progress SNAPSHOT. Master contains the latest stable release

## rest-server

Dockerfile for rest-server is in docker/Dockerfile
comments were removed from file because will cause issues in future versions
Updated env PATH

