#!/usr/bin/env bash

wget https://github.com/actionml/harness/archive/develop.zip
unzip develop.zip
./harness-develop/rest-server/make-distribution.sh
tar zxvf ./harness-develop/rest-server/Harness-0.1.0-SNAPSHOT.tar.gz
echo './Harness-0.1.0-SNAPSHOT/bin/harness -help'
cd ./Harness-0.1.0-SNAPSHOT/bin/harness -help