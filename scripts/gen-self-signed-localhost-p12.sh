#!/usr/bin/env sh

# Generate a self-signed certificate for localhost in p12 format

openssl req -new -newkey rsa:2048 -nodes -keyout localhost.key -out localhost.csr -subj "/CN=localhost"
openssl x509 -req -days 365 -in localhost.csr -signkey localhost.key -out localhost.crt
openssl pkcs12 -export -out localhost.p12 -inkey localhost.key -in localhost.crt -name "localhost"
