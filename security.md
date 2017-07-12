# Security

pio-kappa optionally uses SSL and OAuth2 Server to Server Authentication to secure both ends of the Transport level Security (TLS) connection.

## SSL Support

Uses [http-akka SSL support](http://doc.akka.io/docs/akka-http/current/scala/http/server-side-https-support.html).

## Authentication (not implemented)

We will use token exchange and nonce-based signature authentication. This requires offline exchange of credentials but is quite secure, especially when combined with TLS/SSL. The Client generates a random nonce, which is used with the private key to hash the message being sent. When a REST route is accessed the nonce is read and used with the server's copy of the key to hash the message. If they match (for any key the server knows) the request is said to be authenticated.

## Authorization (not implemented)

If an authenticated route has been granted access permission for the key, then the request will be processed, otherwise it will be refused with an error code.

The generation of keys and the granting of permissions is done through the CLI (since it has global permissions) and so can be set and modified while the Harness Server is running.