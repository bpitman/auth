# TLS Certificates for Local Development

## Root CA (valid 10 years)

```bash
openssl req -x509 -newkey rsa:4096 \
  -keyout root-ca.key -out root-ca.pem \
  -days 3650 -nodes -subj "/CN=localhost-dev-root"
```

## Server certificate (valid 13 months)

```bash
openssl req -newkey rsa:4096 \
  -keyout localhost.key -out localhost.csr \
  -nodes -subj "/CN=localhost"

openssl x509 -req \
  -in localhost.csr -out localhost.pem \
  -CA root-ca.pem -CAkey root-ca.key -CAcreateserial \
  -days 395 \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth")
```
