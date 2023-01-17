CoAP's library for JVM
======================

![Maven Central](https://img.shields.io/maven-central/v/io.github.open-coap/coap-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](LICENSE)
![Status:Production](https://img.shields.io/badge/Project%20status-Production-brightgreen.svg)
[![codecov](https://codecov.io/gh/open-coap/java-coap/branch/master/graph/badge.svg?token=8XE69RTQIZ)](https://codecov.io/gh/open-coap/java-coap)

Introduction
------------

This library makes it possible to create jvm enabled device or coap based services. It can also help to emulate an
embedded device for prototyping and testing purposes.

The following features are supported by the library:

* Complete CoAP support
    - The Constrained Application Protocol [RFC 7252](https://tools.ietf.org/html/rfc7252)
    - Observing Resources in the Constrained Application Protocol [RFC 7641](https://tools.ietf.org/html/rfc7641)
    - Block-Wise Transfers in the Constrained Application Protocol [RFC 7959](https://tools.ietf.org/html/rfc7959)
* CoRE Link Format processing API
    - Constrained RESTful Environments (CoRE) Link Format [RFC 6690](https://tools.ietf.org/html/rfc6690)
* CoAP server mode
* CoAP client mode
* Coap over tcp, tls [RFC 8323](https://tools.ietf.org/html/rfc8323)
    - excluding: websockets, observations with BERT blocks
* Network transports:
  - UDP (plain text)
  - TCP (plain text)
  - DTLS 1.2 with CID (using mbedtls)
    - X509 Certificate
    - PSK
  - TLS
* LwM2M TLV and JSON data formats

Runtime requirements
------------

* JRE 8, 11, 17

Using the Library
-----------------

### Gradle

Add to your `build.gradle.kts`

```kotlin
dependencies {
  ...
  implementation("io.github.open-coap:coap-core:VERSION")
  implementation("io.github.open-coap:coap-mbedtls:VERSION") // for DTLS support
  implementation("io.github.open-coap:coap-tcp:VERSION")     // for coap over tcp support
}
```

### Maven

Add dependency into your `pom.xml`:

```xml

<dependency>
  <groupId>io.github.open-coap</groupId>
  <artifactId>coap-core</artifactId>
  <version>{VERSION}</version>
</dependency>
```

### Coap client simple usage:

```java
// build CoapClient that connects to coap server which is running on port 5683
CoapClient client = CoapClientBuilder
        .newBuilder(new InetSocketAddress("localhost", 5683))
        .build();

// send request
CoapResponse resp = client.sendSync(CoapRequest.get("/sensors/temperature"));
LOGGER.info(resp.toString());

client.close();
```

### Coap client complete usage:

```java
// build CoapClient that connects to coap server which is running on port 5683
CoapClient client = CoapClientBuilder
        // create new builder and sets target server address
        .newBuilder(new InetSocketAddress("localhost", 5683))
        // define transport, plain text UDP listening on random port
        .transport(new DatagramSocketTransport(0))
        // (optional) define maximum block size
        .blockSize(BlockSize.S_1024)
        // (optional) set maximum response timeout
        .finalTimeout(Duration.ofMinutes(2))
        // (optional) set maximum allowed resource size
        .maxIncomingBlockTransferSize(1000_0000)
        // (optional) set extra filters (interceptors) to outbound pipeline
        .outboundFilter(
                // each request will be set with different Token
                TokenGeneratorFilter.sequential(1)
        )
        .build();

// send ping
client.ping();

// send request
CompletableFuture<CoapResponse> futureResponse = client.send(CoapRequest.get("/sensors/temperature"));
futureResponse.thenAccept(resp ->
        // .. handle response
        LOGGER.info(resp.toString())
);

// send request with payload and header options
CompletableFuture<CoapResponse> futureResponse2 = client.send(CoapRequest
        .post("/actuator/switch")
        .options(opt -> {
            // set header options, for example:
            opt.setEtag(Opaque.decodeHex("0a8120"));
            opt.setAccept(MediaTypes.CT_APPLICATION_JSON);
            opt.setMaxAge(3600L);
        })
        .payload("{\"power\": \"on\"}", MediaTypes.CT_APPLICATION_JSON)
);
futureResponse2.thenAccept(resp ->
        // .. handle response
        LOGGER.info(resp.toString())
);

// observe resource (subscribe), observations will be handled to provided callback
CompletableFuture<CoapResponse> resp3 = client.observe("/sensors/temperature", coapResponse -> {
    LOGGER.info(coapResponse.toString());
    return true; // return false to terminate observation
});
LOGGER.info(resp3.join().toString());

client.close();
```

### Server usage

```java
server = CoapServer.builder()
        // configure with plain text UDP transport, listening on port 5683
        .transport(new DatagramSocketTransport(5683))
        // define routing
        // (note that each resource function is a `Service` type and can be decorated/transformed with `Filter`)
        .route(RouterService.builder()
                .get("/.well-known/core", req -> completedFuture(
                        CoapResponse.ok("</sensors/temperature>", MediaTypes.CT_APPLICATION_LINK__FORMAT)
                ))
                .post("/actuators/switch", req -> {
                    // ...
                    return completedFuture(CoapResponse.of(Code.C204_CHANGED));
                })
                // observable resource
                .get("/sensors/temperature", req -> {
                    CoapResponse resp = CoapResponse.ok("21C").nextSupplier(() -> {
                                // we need to define a promise for next value
                                CompletableFuture<CoapResponse> promise = new CompletableFuture();
                                // ... complete once resource value changes
                                return promise;
                            }
                    );

                    return completedFuture(resp);
                })
        )
        .build()
        
server.start();
```

### Services and Filters

All requests are handled by implementing `Service<REQ, RES>` interface, which is a simple function:

```
(REQ) -> CompletableFuture<RES>
```

Intercepting is achieved by implementing `Filter` interface, which is again a simple function:

```
(REQ, Service<IN_REQ, IN_RES>) -> CompletableFuture<RES>
```

Filter interface has a set of helper functions to compose with another `Filter` and `Service`.
Together it creates a pipeline of request handling functions.

It is following "server as a function" design concept. It is a very simple, flexible and testable way to model data processing in a pipeline.
It is best describe in this white paper: [Your Server as a Function](https://monkey.org/~marius/funsrv.pdf), and has a great implementation in [Finagle](https://twitter.github.io/finagle) project.

#### Decorating services with filters

Every `Service` implementation can be decorated with `Filter`. It can be used to implement any kind of authorisation, authentication, validation, rate limitations etc.

For example, if we want to limit allowed payload size, it could be done:

```
  MaxAllowedEntityFilter filter = new MaxAllowedEntityFilter(100, "too big")
  
  Service<CoapRequest, CoapResponse> filteredRoute = filter.then(route)
```

Another example, is to use auto generated `etag` for responses and validate it in requests:

```
  EtagGeneratorFilter filter2 = new EtagGeneratorFilter()
  EtagValidatorFilter filter3 = new EtagValidatorFilter()
  
  Service<CoapRequest, CoapResponse> filteredRoute = filter3.andThen(filter2).then(route)
```

All request handling filters are under package [..coap.server.filter](coap-core/src/main/java/com/mbed/coap/server/filter).


Example client
--------------

This [example client](example-client) demonstrates how to build coap client.


Development
-----------

### Requirements:

* JDK 8
* gradle

### Useful commands:

- `./gradlew build`                             build
- `./gradlew publishToMavenLocal`               publish to local maven
- `./gradlew dependencyUpdates`                 determine which dependencies have updates
- `./gradlew useLatestVersions`                 update dependencies to the latest available versions

Contributions
-------------

All contributions are Apache 2.0. Only submit contributions where you have authored all of the code. If you do this on work time make sure your employer is OK with this.

License
-------

Unless specifically indicated otherwise in a file, files are licensed under the Apache 2.0 license, as can be found in: [LICENSE](LICENSE)
