import actionml

if __name__ == '__main__':
    url = "http://localhost:9090"
    user_id = 'ad21131d-92fe-4885-939c-8ce96c4f6c26'
    user_secret = 'K293EUW18EGBZZf8hJa4V8ruNsFOhiquJmnLaZrF3bjsNe0r7ZXfyAx81Bb9SiLR'

    users_client = actionml.UsersClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    response = users_client.create_user(role_set_id="client", resource_id="*")
    print(response)

    permissions_client = actionml.PermissionsClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    response = permissions_client.grant_permission(role_set_id="admin", resource_id="*",
                                                   permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
    response = permissions_client.revoke_permission(role_set_id="admin", permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
