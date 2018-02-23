#!/usr/bin/env python

from harness import EnginesClient, HttpError

from common import *

engine_client = EnginesClient(
    url=url,
    user_id=client_user_id,
    user_secret=client_user_secret
)


if args.action == 'create':
    with open(args.config) as data_file:
        config = json.load(data_file)
        try:
            res = engine_client.create(config)
            print_success(res, 'Created new engine: ')
        except HttpError as err:
            print_failure(err, 'Error creating new engine\n')

elif args.action == 'update':
    engine_id, config = id_and_config()
    # print("Engine-id: " + engine_id)
    # print("Json config: \n" + str(config))
    try:
        res = engine_client.update(engine_id=engine_id, data=config)
        # print_success_string('Updating engine-id: {} \n'.format(engine_id))
        print_success(res, 'Updating engine: \n')
    except HttpError as err:
        print_failure(err, 'Error updating engine-id: {}\n'.format(engine_id))

#    with open(args.config) as data_file:
#        config = json.load(data_file)
#        engine_id = config.engine_id
#        try:
#            res = engine_client.update(config)
#            print_success(res, 'Updating engine: ')
#        except HttpError as err:
#            print_failure(err, 'Error updating engine\n')

#    engine_id, config = id_or_config()
#    try:
#        res = engine_client.update(engine_id, config, args.delete, args.force, args.input)
#        print_success(res, 'Updating existing engine. Success:\n')
#    except HttpError as err:
#        print_failure(err, 'Error updating engine.')

elif args.action == 'delete':
    engine_id, config = id_or_config()
    try:
        res = engine_client.delete(engine_id=engine_id)
        print_success_string('Deleted engine-id: {} \n'.format(engine_id))
    except HttpError as err:
        print_failure(err, 'Error deleting engine-id: {}\n'.format(engine_id))

elif args.action == 'status':
    engine_id = args.engineid
    try:
        if engine_id is not None:
            res = engine_client.get(engine_id=engine_id)
            # print(str(res))
            print_success(res, 'Status for engine-id: {}\n'.format(engine_id))
        else:
            res = engine_client.get(engine_id=None)
            # print(str(res))
            print_success(res, 'Status for all Engines:\n')
    except HttpError as err:
        print_failure(err, 'Error getting status.\n')

else:
    print_warning("Unknown action: %{}".format(args.action))
