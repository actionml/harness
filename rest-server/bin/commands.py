#!/usr/bin/env python

from actionml import CommandClient, HttpError

from common import *

command_client = CommandClient(
    url=url,
    user_id=client_user_id,
    user_secret=client_user_secret
)


if args.action == 'train':
    try:
        res = command_client.run_command(engine_id=args.engine_id)
        print_success(res, 'Run train for engine_id={}, job_id='.format(args.engine_id))
    except HttpError as err:
        print_failure(err, 'Error running train for engine_id={}'.format(args.engine_id))

elif args.action == 'status':
    try:
        res = command_client.get_status()
        print_success(res, 'Connection to Harness[{}] is '.format(url))
    except HttpError as err:
        print_failure(err, 'Error connecting to Harness[{}]'.format(url))

else:
    print_warning("Unknown action: %{}".format(args.action))
