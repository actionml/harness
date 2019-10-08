#!/usr/bin/env bash

export PW=`pwgen -Bs 128 1`
echo $PW > password
export PW=`cat password`