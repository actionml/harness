import actionml

if __name__ == '__main__':
    url = "http://localhost:9099"
    client_id = "Aladdin"
    client_secret = "OpenSesame"
    user_id = 'f7731339-bc0d-4885-aa1c-2cd37e689ef0'
    user_secret = 'yvr65Ygf07BtIJMhaUcHm5HSmr5U6IwNS5pHo9A71hEYBFnQHYzQY8fU1nd94BhM'

    users_client = actionml.UsersClient(url=url, threads=5, qsize=500, client_id=client_id, client_secret=client_secret)
    response = users_client.create_user(user_id=user_id, user_secret=user_secret, role_set_id="admin", resource_id="*")
    print(response)

    permissions_client = actionml.PermissionsClient(url=url, threads=5, qsize=500, client_id=client_id, client_secret=client_secret)
    response = permissions_client.grant_permission(user_id=user_id, user_secret=user_secret,
                                                   role_set_id="admin", resource_id="*", permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
    response = permissions_client.revoke_permission(user_id=user_id, user_secret=user_secret,
                                                    role_set_id="admin", permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
