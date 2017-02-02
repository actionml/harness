# Security

pio-kappa optionally uses SSL and OAuth2 Server to Server Authentication to secure both ends of the Transport level Security (TLS) connection.

## SSL Support

Uses [http-akka SSL support](http://doc.akka.io/docs/akka-http/current/scala/http/server-side-https-support.html).

## OAuth2 Authentication

We will use OAuth2 authorization framework. OAuth2 defines 4 grant types: "authorization code", "implicit", "password credentials", and "client credentials". Since we need server-to-server auth, then we will use ["client credentials" grant type](https://tools.ietf.org/html/rfc6749#section-4.4) Thus our rest service will be a "resource server" and "authorization server". We will use the [Nulab library](https://github.com/nulab/scala-oauth2-provider) for the implementation.  
