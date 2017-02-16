#!/usr/bin/env bash

export PW=`cat password`

# Create a server certificate, tied to localhost
keytool -genkeypair -v \
  -alias localhost \
  -dname "CN=localhost, OU=ActionML, O=ActionML, L=San Francisco, ST=California, C=US" \
  -keystore localhost.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385

# Create a certificate signing request for localhost
keytool -certreq -v \
  -alias localhost \
  -keypass:env PW \
  -storepass:env PW \
  -keystore localhost.jks \
  -file localhost.csr

# Tell rootCA to sign the localhost certificate. Note the extension is on the request, not the
# original certificate.
# Technically, keyUsage should be digitalSignature for DHE or ECDHE, keyEncipherment for RSA.
keytool -gencert -v \
  -alias rootCA \
  -keypass:env PW \
  -storepass:env PW \
  -keystore rootCA.jks \
  -infile localhost.csr \
  -outfile localhost.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:localhost" \
  -rfc

# Tell localhost.jks it can trust rootCA as a signer.
keytool -import -v \
  -alias rootCA \
  -file rootCA.crt \
  -keystore localhost.jks \
  -storetype JKS \
  -storepass:env PW << EOF
yes
EOF

# Import the signed certificate back into localhost.jks
keytool -import -v \
  -alias localhost \
  -file localhost.crt \
  -keystore localhost.jks \
  -storetype JKS \
  -storepass:env PW

# List out the contents of localhost.jks just to confirm it.
# If you are using Play as a TLS termination point, this is the key store you should present as the server.
keytool -list -v \
  -keystore localhost.jks \
  -storepass:env PW