import actionml

if __name__ == '__main__':
    url = "http://localhost:9090"
    user_id = '57df7d8b-309e-48e5-b2b6-41e1c9ac6b04'
    user_secret = '72iMmNw0ohio0sEZ2nTzcnaWkfgMi5LZFvft5pRxj4KiNEga2R2mNOLV6ivDTRSe'

    users_client = actionml.UsersClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    response = users_client.create_user(role_set_id="client", resource_id="*")
    print(response)

    permissions_client = actionml.PermissionsClient(url=url, threads=5, qsize=500, user_id=user_id, user_secret=user_secret)
    response = permissions_client.grant_permission(role_set_id="admin", resource_id="*",
                                                   permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
    response = permissions_client.revoke_permission(role_set_id="admin", permitted_user_id="9d563346-6f72-4030-aecb-0689e342a34c")
    print(response)
