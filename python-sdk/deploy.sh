#!/usr/bin/env bash

python3 setup.py register -r pypitest
python3 setup.py sdist upload -r pypitest