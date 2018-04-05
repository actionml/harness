import json
import os
import re

import argparse

parser = argparse.ArgumentParser()
parser.add_argument("action", type=str)
parser.add_argument("engine_id", type=str, nargs="?", default=None)
parser.add_argument("-c", "--config", type=str, default=None)
parser.add_argument("-u", "--user_id", type=str, default=None)
parser.add_argument("--client_user_id", type=str, default=None)
parser.add_argument("--client_user_secret_location", type=str, default=None)
parser.add_argument("-r", "--role_set", type=str, default=None)
parser.add_argument("-e", "--engineid", type=str, default=None)
parser.add_argument("--data_delete", "-d", dest='delete', default=False, action='store_true')
parser.add_argument("--force", "-f", default=False, action='store_true')
parser.add_argument("--import_path", "-i", type=str, default=None)
parser.add_argument("--all_engines", "-a", default=False, action='store_true')
parser.add_argument("--all_users", default=False, action='store_true')
args = parser.parse_args()

harness_host = os.getenv('HARNESS_EXTERNAL_ADDRESS', 'localhost')
harness_port = os.getenv('REST_SERVER_PORT', 9090)

if os.getenv('HARNESS_AUTH_SERVER_PROTECTED') == 'true' or os.getenv('HARNESS_AUTH_ENABLED') == 'true':
    auth_enabled = True
    # print('Auth enabled')
else:
    auth_enabled = False
    # print('Auth disabled')

if os.getenv('HARNESS_SSL_ENABLED') == 'true':
    url = 'https://{}:{}'.format(harness_host, harness_port)
else:
    url = 'http://{}:{}'.format(harness_host, harness_port)

client_user_id = None
client_user_secret = None

if args.client_user_id is not None and args.client_user_secret_location is not None and auth_enabled:
    client_user_id = args.client_user_id
    with open(args.client_user_secret_location) as secret_file:
        client_user_secret = secret_file.read().rstrip("\n")
        # print('Auth enabled with user_id: {} and secret: {}'.format(client_user_id, client_user_secret))
else:
    if auth_enabled:
        raise RuntimeError('User_id and secret not passed in when auth is enabled')
        # print('No user_id or secret')

class BColors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    RED = '\033[91m'
    END = '\033[0m'

def strip_valid(text):
    # print(text)
    """
    begin_valid = re.compile('Valid') # , re.MULTILINE)
    end_valid = re.compile('^ *\)$') # , re.MULTILINE)
    new_text = begin_valid.sub(text, '')
    print('subbed text: \n' + new_text)
    print('unsubbed text: \n' + text)
    new_text = end_valid.sub(new_text, '')
    """
    if text == "True":
        return "Success"
    lines = text.splitlines()
    begin_valid = re.compile('\,Valid\(')
    begin_paren = re.compile('^\(')
    new_lines = []
    for line in lines:
        if line != 'Valid(' and line != '    )' and line != '    ))':
            new_line = begin_valid.sub('', line)
            new_line = begin_paren.sub('\nEngine-id: ', new_line)
            new_lines.append(new_line + '\n')
    return ''.join(new_lines)


def pp_json(json_thing, sort=True, indents=4):
    if type(json_thing) is str:
        try:
            # print("got string")
            return json.dumps(json.loads(json_thing), sort_keys=sort, indent=indents)
        except ValueError:
            # print("Not Valid JSON: " + json_thing + " trying to strip any Valid wrapper")
            return strip_valid(json_thing)
    else:
        # print("got JSON")
        return json.dumps(json_thing, sort_keys=sort, indent=indents)


# Use when the json response should be printed
def print_success(res, text):
    print(BColors.GREEN + text + pp_json(res.json_body) + BColors.END)


# Use when there is no need to print the response
def print_success_string(text):
    print(BColors.GREEN + text + BColors.END)


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

def id_and_config():
    engine_id = None
    config = {}

    if args.config is not None:
        with open(args.config) as data_file:
            config = json.load(data_file)

    if args.engine_id is None:
        engine_id = config['engineId']

    return engine_id, config


"""
def user_id():
    print(args)
    user_id=args.user_id
    return user_id
"""

def get_client_user_secret(client_user_secret_location=None):
    if client_user_secret_location is not None:
        try:
            with open(client_user_secret_location) as secret_file:
                client_user_secret = secret_file.read().rstrip("\n")
                print('User secret: {}', client_user_secret)
                return client_user_secret
        except OSError as exc:
            print('{!r}: {}'.format(client_user_secret_location, exc.strerror))
    return None
