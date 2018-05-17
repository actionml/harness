#!/usr/bin/env python

from harness import CommandsClient, HttpError

from common import *

import sys

command_client = CommandsClient(
    url=url,
    user_id=client_user_id,
    user_secret=client_user_secret
)


if args.action == 'train':
    try:
        res = command_client.run_command(engine_id=args.engine_id)
        print_success(res, 'Run train for engine_id={}, job_id='.format(args.engine_id))
        sys.exit(0)
    except HttpError as err:
        print_failure(err, 'Error running train for engine_id={}'.format(args.engine_id))
        sys.exit(1)

elif args.action == 'status':
    try:
        res = command_client.get_status()
        print_success(res, 'Connection to Harness[{}] is '.format(url))
        sys.exit(0)
    except HttpError as err:
        print_failure(err, 'Error connecting to Harness, the server may not be running.\n[{}]'.format(url))
        sys.exit(1)

else:
    print_warning("Unknown action: %{}".format(args.action))
    sys.exit(0)
