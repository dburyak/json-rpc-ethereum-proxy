FROM alpine/java:21-jre

RUN mkdir app
ADD build/install/json-rpc-ethereum-proxy/bin /app/bin
ADD build/install/json-rpc-ethereum-proxy/lib /app/lib

WORKDIR /app
ENTRYPOINT /app/bin/json-rpc-ethereum-proxy
