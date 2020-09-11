#!/bin/sh

# == Install docker composer
apk add --no-cache --update py-pip python3-dev libffi-dev openssl-dev gcc libc-dev make curl curl-dev
pip install docker-compose==1.12.0
docker-compose version 

