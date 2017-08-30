import actionml

if __name__ == '__main__':
    users_client = actionml.UsersClient(url="http://localhost:9099", threads=5, qsize=500, client_id="Aladdin", client_secret="OpenSesame")
    user_id = 'f7731339-bc0d-4885-aa1c-2cd37e689ef0'
    user_secret = 'yvr65Ygf07BtIJMhaUcHm5HSmr5U6IwNS5pHo9A71hEYBFnQHYzQY8fU1nd94BhM'
    response = users_client.create_user(user_id=user_id, user_secret=user_secret, role_set_id="admin", resource_id="*")
    print(response)
