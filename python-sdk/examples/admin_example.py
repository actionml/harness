import actionml

if __name__ == '__main__':
    url = "http://localhost:9090"
    user_id = 'd669a063-b004-42b1-afa5-9d24242464db'
    user_secret = 'UcefLpFXYq6VbZWcE9hEryoE1NCmgsNVoYSvkU4nb5AxGoBN4LrHUxd3HnysVZVE'

    users_client = actionml.UsersClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    # users_client = actionml.UsersClient(url=url, threads=5, qsize=500)
    response = users_client.create_user(role_set_id="admin", resource_id="*")
    print(response)

    # permissions_client = actionml.PermissionsClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    # response = permissions_client.grant_permission(role_set_id="admin", resource_id="*",
    #                                                permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    # print(response)
    # response = permissions_client.revoke_permission(role_set_id="admin", permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    # print(response)
