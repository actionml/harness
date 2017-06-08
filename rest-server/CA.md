# Generating a server CA
The first step is to create a certificate authority that will sign the example.com certificate. The root CA certificate has a couple of additional attributes (ca:true, keyCertSign) that mark it explicitly as a CA certificate, and will be kept in a trust store.

## Generating a random password

```
export PW=`pwgen -Bs 10 1`
echo $PW > password
export PW=`cat password`
```

## Create a self signed key pair root CA certificate.

```
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
```

#### Export the rootCA public certificate as rootCA.crt so that it can be used in trust stores.

```
keytool -export -v \
  -alias rootCA \
  -file rootCA.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore rootCA.jks \
  -rfc
```

## Generating localhost certificates
The localhost certificate is presented by the localhost server in the handshake.

```
export PW=`cat password`
```

#### Create a server certificate, tied to localhost

```
keytool -genkeypair -v \
  -alias localhost \
  -dname "CN=localhost, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US" \
  -keystore localhost.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385
```

#### Create a certificate signing request for localhost

```
keytool -certreq -v \
  -alias localhost \
  -keypass:env PW \
  -storepass:env PW \
  -keystore localhost.jks \
  -file localhost.csr
```

#### Tell localCA to sign the localhost certificate. Note the extension is on the request, not the original certificate. Technically, keyUsage should be digitalSignature for DHE or ECDHE, keyEncipherment for RSA.

```
keytool -gencert -v \
  -alias localCA \
  -keypass:env PW \
  -storepass:env PW \
  -keystore localCA.jks \
  -infile localhost.csr \
  -outfile localhost.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:localhost" \
  -rfc
```

#### Tell localhost.jks it can trust localCA as a signer.

```
keytool -import -v \
  -alias localCA \
  -file localCA.crt \
  -keystore localhost.jks \
  -storetype JKS \
  -storepass:env PW << EOF
yes
EOF
```

#### Import the signed certificate back into localhost.jks

```
keytool -import -v \
  -alias localhost \
  -file localhost.crt \
  -keystore localhost.jks \
  -storetype JKS \
  -storepass:env PW
```

##### List out the contents of localhost.jks just to confirm it. If you are using Play as a TLS termination point, this is the key store you should present as the server.

```
keytool -list -v \
  -keystore localhost.jks \
  -storepass:env PW
```
