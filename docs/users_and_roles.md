# Users and Roles

When using Auth with Harness we define Users and give them Roles. Two Roles are predefined: client and admin. Clients are granted access to any number of Engines by ID and all of their sub-resources: Events and Queries. The admin has access to all parts of Harness. Only an Admin User can manage Users and Roles.

 - **`harness user-add [client <engine-id> | admin]`** returns the user-id and secret that gives client access to the engine-id specified OR gives the user-id admin global superuser access.
 - **`harness user-delete <some-user-id>`** removes the user and any permissions they have, in effect revoking their credentials. A warning will be generated when deleting an admin user.
 - **`harness grant <user-id> [client <engine-id> | admin]`** modifies some existing user-id's access permissions, including elevating the user to admin super user status.
 - **` harness revoke <user-id> [client <engine-id>| admin]`** revokes some existing permission for a user
 - **`harness status users [<some-user-id>]`** list all users and their permissions or only permissions for requested user.

