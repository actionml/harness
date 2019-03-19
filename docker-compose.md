# Install Using Docker-Compose

Harness and all services it depends on run in Docker containers. This makes it fairly easy to install on a single machine for experiments or when only vertical scaling is required.

The easiest way to do this is use the `docker-compose.yml` file to bring up containers for MongoDB, Elasticsearch (needed by the UR), and Harness.

The Docker Compose network maps certain directories inside containers into the host filesystem so you can configure and manage persistence for the databases, and see logs.

There are 2 forms of the Docker Compose, one with all required services including the Harness-Auth Server and Vowpal Wabbit for the Contextual Bandit Engine.

We will first discuss docker-compose for the Universal Recommender, without authentication or TLS.

# Simple Docker-Compose

Make sure you have a running Docker daemon with CLI installed. These instructions assume you are using Ubuntu. **Note**: Docker for macOS and Windows runs in a VM and so has subtle differences that may cause some problems. Please report these in the Harness repo's issues area.

 - Clone the Harness-CLI project from its [repo here](https://github.com/actionml/harness-cli). Make sure you are in the `feature/cli-refactor` branch.
 - Install it on the host OS (a container is not available yet) following instructions in the README.md
 - Clone the Harness project from its [repo here](https://github.com/actionml/harness/tree/develop)
 - make sure you are in the `develop` branch.

## Configure

The compose will map container directories into the host filesystem for MongoDB and Elasticsearch data as well as Harness config. These are prepared by cloning the Harness repo. For a simple localhost installation no further config is required.

## Deploy

 - `cd harness`
 - `docker-compose up -d --build` for first time setup
 - `git pull && docker-compose down && docker-compose up -d --build --force-recreate` to update harness and takedown old containers and create new containers with new harness version

## Using the Harness CLI

Harness must be told which Engines to use and how to configure them. To do this we use the Harness CLI. It communicates with a Harness Server via its REST API. Install following instructions in the README.md for the [repo here](https://github.com/actionml/harness-cli)

The default config points to Harness on `localhost:9090` which should be fine for the simple docker-compose method of deployment. If this doesn't connect change `HARNESS_SERVER_ADDRESS=harness` to use the container name for addressing and leave port 9090 as pre-configured. 

Some further notes may be useful in [Alfonso-notes.md](Alfonso-notes.md)