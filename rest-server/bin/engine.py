#!/usr/bin/python

import argparse
import json
import os

from actionml import EngineClient, HttpError

parser = argparse.ArgumentParser()
parser.add_argument("action", type=str)
parser.add_argument("engine_id", type=str, nargs="?", default=None)
parser.add_argument("--config", "-c", type=str, default=None)
args = parser.parse_args()


class BColors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    RED = '\033[91m'
    END = '\033[0m'


def print_success(res, text):
    print(BColors.GREEN + text + str(res.json_body) + BColors.END)


def print_failure(err, text):
    if err.response.json_body is not None:
        print(BColors.RED + text + ' ' + str(err.response.json_body) + BColors.END)
    else:
        print(BColors.RED + text + ' ' + str(err.response.error) + BColors.END)


def print_warning(notice):
    print(BColors.WARNING + notice + BColors.END)


harness_host = os.getenv('HARNESS_REST_HOST', 'localhost')
harness_port = os.getenv('HARNESS_REST_PORT', 9090)

engine_client = EngineClient(url='http://{}:{}'.format(harness_host, harness_port))

if args.action == 'get':
    pass

elif args.action == 'create':
    with open(args.config) as data_file:
        config = json.load(data_file)
        try:
            res = engine_client.create(config)
            print_success(res, 'Created new engine. Success=')
        except HttpError as err:
            print_failure(err, 'Error creating new engine.')

elif args.action == 'update':
    with open(args.config) as data_file:
        config = json.load(data_file)
        try:
            res = engine_client.create(config)
            print_success(res, 'Updates exists engine. Success=')
        except HttpError as err:
            print_failure(err, 'Error updating engine.')

elif args.action == 'delete':
    try:
        res = engine_client.delete(engine_id=args.engine_id)
        print_success(res, 'Deleted engine id={} is '.format(args.engine_id))
    except HttpError as err:
        print_failure(err, 'Error deleting engine id={}'.format(args.engine_id))

else:
    print_warning("Unknown action: %{}".format(args.action))
