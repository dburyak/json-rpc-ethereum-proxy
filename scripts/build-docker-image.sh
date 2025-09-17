#!/usr/bin/env sh

# For simplicity, this script can be executed only from the project root directory.

# builds distribution files (jars + startup shell scripts) and puts them in build/install
./gradlew :install

# builds docker image
docker build -t json-rpc-ethereum-proxy:latest -f Dockerfile .

# NOTE: you must provide at least `JSONRPC_PROXIED_BACKEND_URLS` env var to the container at runtime
# Example:
# docker run -p 8080:8080 -e JSONRPC_PROXIED_BACKEND_URLS="https://eth.llamarpc.com, https://ethereum.rpc.subquery.network/public" json-rpc-ethereum-proxy:latest
