#!/usr/bin/env bash

echo "Usage: ./prep-for-int-test.sh"
echo "This test wipes the Harness DB and creates/cleans up \"/tmp/harness/models and .../mirrors"
echo "This allows the integration tests to run as if they were on a new machine but destroys DB data!"

read -r -p "Are you sure you want to wipe you Harness environment clean? [y/N] " response
case "$response" in
    [yY][eE][sS]|[yY])
        echo "OK continuing with wipe and cleanup"
        echo
        ;;
    *)
        echo "OK aborting."
        echo
        exit 1
        ;;
esac


num_harness_instances=` ps aux | egrep "com\.actionml\.HarnessServer" | wc -l`

if [[ $num_harness_instances -gt 0 ]]; then
    echo "Harness may be running, we need to stop it to wipe the DB"

    read -r -p "Are you sure you want to stop Harness? [y/N] " response
    case "$response" in
        [yY][eE][sS]|[yY])
            harness stop
            ;;
        *)
            echo "OK aborting."
            exit 1
            ;;
    esac
fi

# wipe mongo of any data, this assume harness is not running!
echo "Dropping Harness DBs"
mongo clean_harness_mongo.js # drops meta store (all engines) and specific dbs used by test engine instances
echo

# used by contextual bandit and maybe other Engines
echo "Setting up clean model and mirror space"
echo

rm -r /tmp/harness
mkdir -p /tmp/harness/models
mkdir -p /tmp/harness/mirrors
