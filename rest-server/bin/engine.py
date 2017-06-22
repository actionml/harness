#!/usr/bin/env python

from actionml import EngineClient, HttpError

from common import *

engine_client = EngineClient(url=url)

#debug
#print("Action: "+args.action+" all args: "+str(args))
#print("Input: "+args.input)
if args.action == 'create':
    with open(args.config) as data_file:
        config = json.load(data_file)
        try:
            res = engine_client.create(config)
            print_success(res, 'Created new engine. Success=')
        except HttpError as err:
            print_failure(err, 'Error creating new engine.')

elif args.action == 'import':
    i = args.input
    engine_id = args.engine_id
    print("Importing to: "+engine_id+" from: "+i)
    try:
        res = engine_client.update(engine_id, i)
        print_success(res, 'Updating existing engine. Success=')
    except HttpError as err:
        print_failure(err, 'Error updating engine.')

elif args.action == 'update':
    engine_id, config = id_or_config()
    try:
        res = engine_client.update(engine_id, config, args.delete, args.force)
        print_success(res, 'Updating existing engine. Success=')
    except HttpError as err:
        print_failure(err, 'Error updating engine.')

elif args.action == 'delete':
    engine_id, config = id_or_config()
    try:
        res = engine_client.delete(engine_id=engine_id)
        print_success(res, 'Deleted engine id={} is '.format(engine_id))
    except HttpError as err:
        print_failure(err, 'Error deleting engine id={}'.format(engine_id))

else:
    print_warning("Unknown action: %{}".format(args.action))
