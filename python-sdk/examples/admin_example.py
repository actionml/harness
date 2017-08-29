import actionml

if __name__ == '__main__':
    users_client = actionml.UsersClient(url="http://localhost:9090", threads=5, qsize=500)
    user_id = '29090802-1cb2-47c0-80f3-9b271dd7dee6'
    user_secret = '000000000000000000000000a6e9c336e13819cf1dd0222d1ae5e53a97c3ef46'
    response = users_client.create_user(user_id, user_secret, role_set_id="event_read", resource_id="*")
    print(response)
