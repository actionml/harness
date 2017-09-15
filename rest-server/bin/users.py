#!/usr/bin/env python

from harness import HttpError, UsersClient, PermissionsClient

from common import *

# print('user: {} secret: {}'.format(client_user_id, client_user_secret))

if args.action == 'user-add':
    role_set = args.role_set
    engine_id = args.engineid
    users_client = UsersClient(
        url=url,
        user_id=client_user_id,
        user_secret=client_user_secret
    )
    try:
        if role_set == 'client':
            res = users_client.create_user(role_set_id=role_set, resource_id=engine_id)
            print_success(res, 'Added user: '.format())
        elif role_set == 'admin':
            res = users_client.create_user(role_set_id=role_set, resource_id='*')
            print_success(res, 'Added user: '.format())
        else:
            print("Whoopsie, bad role")
    except HttpError as err:
        print_failure(err, 'Error creating new user\n')

elif args.action == 'user-delete':
    user_id = args.user_id
    users_client = UsersClient(
        url=url,
        user_id=client_user_id,
        user_secret=client_user_secret
    )
    try:
        # res = users_client.delete(u)
        # print_success(res, 'Deleted user: {} Success:\n'.format(user_id))
        print("Deleting user: {}".format(user_id))
        print("Not implemented yet, try revoking permissions")
    except HttpError as err:
        print_failure(err, 'Error deleting user.')

elif args.action == 'grant':
    role_set = args.role_set
    user_id = args.user_id
    if role_set == 'admin':
        engine_id = '*'
    else:
        engine_id = args.engineid  # non-positional engine-id passed as a param
    permissions_client = PermissionsClient(
        url=url,
        user_id=client_user_id,
        user_secret=client_user_secret
    )

    try:
        res = permissions_client.grant_permission(permitted_user_id=user_id, role_set_id=role_set, resource_id=engine_id)
        # print_success(res, 'Added permissions for user: {} Success:\n'.format(user_id))
        print_success(res, 'Granting permission for user: {} to act as: {} for engine-id: {} '.format(user_id, role_set, engine_id))
    except HttpError as err:
        print_failure(err, 'Error granting permission for user: {}\n'.format(user_id))

elif args.action == 'revoke':
    role_set = args.role_set
    user_id = args.user_id
    if role_set == 'admin':
        engine_id = '*'
    else:
        engine_id = args.engineid  # non-positional engine-id passed as a param
    permissions_client = PermissionsClient(
        url=url,
        user_id=client_user_id,
        user_secret=client_user_secret
    )

    try:
        res = permissions_client.revoke_permission(permitted_user_id=user_id, role_set_id=role_set)
        # print_success(res, 'Added permissions for user: {} Success:\n'.format(user_id))
        print_success(res, 'Revoking permission for user: {} role: {} '.format(user_id, role_set))
    except HttpError as err:
        print_failure(err, 'Error revoking permission for user: {}\n'.format(engine_id))

elif args.action == 'status':
    user_id = args.userid

    try:
        if user_id is not None:
            # res = permissions_client.get(user_id)
            # print_success(res, 'Added permissions for user: {} Success:\n'.format(user_id))
            print('Getting status for user: {}'.format(user_id))
        else:
            # res = permissions_client.get(user_id)
            # print_success(res, 'Added permissions for user: {} Success:\n'.format(user_id))
            print('Getting status for all users')
    except HttpError as err:
        print_failure(err, 'Error deleting permission for user: {}\n'.format(engine_id))

else:
    print_warning("Unknown action: %{}".format(args.action))
