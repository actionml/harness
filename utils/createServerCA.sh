#!/usr/bin/env bash

export PW=`cat password`

# Create a self signed key pair root CA certificate.
keytool -genkeypair -v \
  -alias rootCA \
  -dname "CN=rootCA, OU=ActionML, O=ActionML, L=San Francisco, ST=California, C=US" \
  -keystore rootCA.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999

# Export the rootCA public certificate as rootCA.crt so that it can be used in trust stores.
keytool -export -v \
  -alias rootCA \
  -file rootCA.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore rootCA.jks \
  -rfc