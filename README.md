# json-rpc-ethereum-proxy

An exercise project to create a JSON-RPC proxy for Ethereum nodes.

# Disclaimer

This is my first time working with blockchain technology and Ethereum, so I
still don't fully understand how everything works. Hence, for this exercise I'm
assuming that Ethereum nodes are just like any other distributed backend system
that exposes an API. In other words, this proxy implementation is not really
Ethereum-specific, and could be used for any other JSON-RPC backend. This may be
a wrong assumption, so please take this into account when reading the code.

# Requirements

I've refined the requirements for this exercise to make it more realistic and
interesting. Particularly, my thoughts are based on my experience with nginx and
its features.

* The proxy should forward requests to *multiple* Ethereum nodes, not just one.
  I.e. it should balance the load for proxied instances. This makes sense for a
  typical proxy use case, for example ingress-controller in a k8s cluster, where
  it balances requests among the backend app instances. This may be a wrong
  assumption for Ethereum nodes, in which case we can just always configure a
  *single* Ethereum node and there won't be any effect of load balancing.

* It should be scalable, i.e. it should be possible to run multiple instances of
  the proxy and the behavior should be the same as if there was a single
  instance.

* TLS termination is implemented but disabled by default. This is in order to
  make it easier to run it anywhere out of the box, without having to deal with
  certificates. To use TLS, you'll need to provide three env vars:
    - `JSONRPC_TLS_ENABLED` - set to `true` to enable TLS
    - `JSONRPC_TLS_P12_PATH` - path to p12 file
    - `JSONRPC_TLS_P12_PASSWORD` - password for the p12 cert

  Only p12 format is supported for simplicity. To generate a self-signed cert in
  p12 format for running on localhost, you can use the bash script in `scripts`.

* Rate limiting by IP. There will be two rate limiters - one global for all
  requests, and another more fine-grained one for each unique JSON-RPC method.

* Access logging. For now, we'll be writing to a local file, but it should be
  easy to add other logging backends.

* Method call tracking by IP and REST API to retrieve this information. This is
  an initial step towards billing. For proper billing, we would need to reliably
  identify users instead of just using IP addresses. API keys would be a good
  choice for that, but this is out of scope for this exercise. Billing
  information is important for business, so it should be accurate and stored
  reliably. I.e. we should store it in a persistent storage and not lose it on
  crashes/restarts. Also, tracking information should be accurate regardless of
  the number of proxy instances.

# Simplifications

## Config

At some point I gave up trying to make everything configurable via env
variables. So, some values are configurable only via config file.

When more and more code was added I started to having regrets about not using
Micronaut DI for this exercise. It would make both configuration and factories
with components lifecycle much easier.

## Call tracking info persistence

Instead of calling Redis directly, a much better approach would be to abstract
the storage access behind a repository interface. This way, we could easily
switch storage backends. It's an easy change, but requires quite a bit of
boilerplate code, so I decided to skip it for this exercise.

## Scripts root directory

For scripts simplicity, I've assumed that the working directory is always the
root directory of the project. They won't work correctly if run from some other
directory.

# Design decisions

## Storage - Redis

First of all, we'll need some kind of persistent storage to track calls for rate
limiting and billing, and for multiple instances to share the same state. There
are two approaches here - either centralized storage (separate storage) or a
distributed memory grid (each proxy instance has part of the data in local RAM)
with persistence support. The latter option is far more complex. Even though in
theory distributed solution may be more scalable and performant.

Hence, I've decided to go with a centralized storage. Redis is an obvious choice
for this kind of use case - speed of in-memory storage with persistence and is
provided as-a-service by many cloud providers. And it has `INCR` command which
is perfect for counting requests.

Two technologies similar to Redis that I know about are Memcached and Aerospike.
Memcached is not persistent, and we can't group keys there to store multiple rpc
methods counters under the same key for easy retrieval. Aerospike is a more
advanced DB with much more features and will be an overkill here.

Using a classic database here would be a bad idea because:

- performance is critical, and databases are far slower than in-memory stores
- data access patterns are very simple and don't require complex queries, access
  by key is sufficient

NOTE: potentially we could use Vertx's shared data structures for this. But they
are implemented using distributed memory grid (hazelcast, ignite or infinispan)
under the hood. It will require substantial effort to make it persistent and
correct without depending on the number of instances. And most likely, if we use
it via Vertx's interface, the result number of network calls may be even higher
than with Redis.

## Caching

Since this kind of application has very high demand for performance, we can
reduce the number of calls to the centralized storage with caching. Obvious
candidates for caching are rate limiters. We don't care about the actual number
of calls from the same IP, but only whether the limit has been exceeded or not
within the given timeframe. And also, counter can only go up, so after it has
reached the limit there is no need to check the storage again until the
timeframe ends.

## Async queued processing

Another performance optimization is to not wait for certain operations to finish
before finishing handling the request. In other words, operations that should be
performed but their result is not needed for the request can be processed
asynchronously (optionally enqueued and processed in batches).

First such operation that I see is tracking method calls for billing. We don't
need to wait for the storage to be updated before forwarding the request to the
Ethereum node. We can just enqueue the operation and process it later. And also
to reduce the network congestion, we can accumulate multiple operations and
process them in a batch.

Similarly, access logging can be done in the same fashion.

## Configuration

There's a chicken-and-egg dependency between Vertx instance and ConfigRetriever:
some options of Vertx instance itself may need to be configurable. For
simplicity, I'll assume that Vertx instance does not need any external
configuration.

But for a more complex app, we'd need to manually parse/load some config(s)
first, then create Vertx instance out of that config, and only after that create
ConfigRetriever to load the rest of the config. In such case I'd rather use a
compile-time DI framework with negligible performance overhead rather than
manually handle config options, plus the benefits of DI itself. I've already
experimented and successfully used MicronautDI with Vertx for this purpose. You
can see more details of our efforts in my colleague's blog post:
https://taraskohut.medium.com/vert-x-micronaut-do-we-need-dependency-injection-in-the-microservices-world-84e43b3b228e
All that was implemented as a reusable library:
https://github.com/dburyak/vertx-tools
Where config could look nicely like this:
https://github.com/dburyak/vertx-tools/blob/main/test-app/src/main/resources/application.yaml
and the app using it can use it in traditional way:

- for type-safe
  configuration: https://github.com/dburyak/vertx-tools/blob/main/test-app/src/main/java/com/dburyak/vertx/test/MemProperties.java
- for injecting directly:
  `SomeConstructor(@Nullable @Property(name = 'vertx.app-name') String cfgAppName`

Initially I wanted to use DI, but decided to not overcomplicate things for this
exercise.

Default approach with `config.json` is slightly inconvenient, so I used yaml
format instead. Environment variables take precedence over config file values.
Environment variables and keys for yaml config file are all available in the
`Config.java` class.

## Extensibility

We'll follow a typical approach for such apps - chain of processors, where each
processor performs a specific task and passes the request (+ any other data) to
the next processor in the chain. This way, we can easily add/remove/modify
processor implementations according to changing requirements. Also, it will be
easy to make the chain configurable to be able to enable/disable certain
features. Another benefit of this approach is that each processor can be tested
in isolation.

# Running

Requirements:
 - java 21
 - Redis

To build the app and generate startup scripts:
```
./gradlew :install
```

## Redis in container

Installing and running Redis with default options will work just fine and is
a better way as it's easier to examine redis state with redis-cli.

To run Redis in a docker container:
```
# for the first time:
docker run -d --name redis -p 6379:6379 redis:latest
# or if you already have a container:
docker container start redis
```

## Docker image

To build the docker image:
```
./scripts/build-docker-image.sh
```

To run both the app and Redis in docker containers:
```
./scripts/build-docker-image.sh
export JSONRPC_PROXIED_BACKEND_URLS="<ETH_URL1>,<ETH_URL2>,..."
docker compose up
```
