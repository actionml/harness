#!/usr/bin/python

import argparse
import os

from actionml import CommandClient, HttpError

parser = argparse.ArgumentParser()
parser.add_argument("action", type=str)
parser.add_argument("engine_id", type=str, nargs="?", default=None)
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


pio_host = os.getenv('PIO_REST_HOST', 'localhost')
pio_port = os.getenv('PIO_REST_PORT', 9090)
url = 'http://{}:{}'.format(pio_host, pio_port)

command_client = CommandClient(url=url)

if args.action == 'train':
    try:
        res = command_client.run_command(engine_id=args.engine_id)
        print_success(res, 'Run train for engine_id={}, job_id='.format(args.engine_id))
    except HttpError as err:
        print_failure(err, 'Error running train for engine_id={}'.format(args.engine_id))

elif args.action == 'status':
    try:
        res = command_client.get_status()
        print_success(res, 'Connect to ActionML Rest Server [{}] is '.format(url))
    except HttpError as err:
        print_failure(err, 'Error connecting to ActionML Rest Server [{}]'.format(url))

else:
    print_warning("Unknown action: %{}".format(args.action))
