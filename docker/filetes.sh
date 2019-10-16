#!/bin/bash
CLUSTER="devboar"

if [[ "$CLUSTER" != "devboard" ]] && [[ "$CLUSTER" != "dev" ]] && [[ "$CLUSTER" != "devopsdev" ]]
      then
         echo "sim"
      else
         echo "não"
      fi

