import json
import os

import argparse

parser = argparse.ArgumentParser()
parser.add_argument("action", type=str)
parser.add_argument("engine_id", type=str, nargs="?", default=None)
parser.add_argument("-c", "--config", type=str, default=None)
parser.add_argument("--data-delete", "-d", dest='delete', default=False, action='store_true')
parser.add_argument("--force", "-f", default=False, action='store_true')
parser.add_argument("--input", "-i", type=str, default=None)
parser.add_argument("--all-engines", "-a", default=False, action='store_true')
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


def id_or_config():
    engine_id = args.engine_id
    config = {}

    if args.config is not None:
        with open(args.config) as data_file:
            config = json.load(data_file)

    if args.engine_id is None:
        engine_id = config['engineId']

    return engine_id, config


harness_host = os.getenv('REST_SERVER_HOST', 'localhost')
harness_port = os.getenv('REST_SERVER_PORT', 9090)

url = 'http://{}:{}'.format(harness_host, harness_port)

if args.engine_id is None and args.config is None and args.action != 'status':
    print_warning('Expect engine_id or config')
    exit(1)
