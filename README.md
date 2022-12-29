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
  - TLS
  - DTLS 1.2 (using mbedtls)
  - DTLS 1.2 with CID (using mbedtls)
* LwM2M TLV and JSON data formats

Requirements
------------

### Runtime:

* JRE 8, 11, 17

### Development:

* JDK 8
* gradle

Using the Library
-----------------

### Gradle

Add to your `build.gradle`

```kotlin
dependencies {
  ...
  implementation("io.github.open-coap:coap-core:VERSION")
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

### Creating a Server

#### Initializing, starting and stopping the server

To initialize a server, you must at minimum define the port number. You must set the server parameters before starting a server.

    CoapServer server = CoapServer.builder().transport(5683).build();
    server.start();

To stop a server, use the `stop()` method.

    server.stop();

#### Handling incoming requests with Services and Filters

Incoming requests are handled by implementing `Service<CoapRequest, CoapResponse>` interface. It is a simple function: 

```
(CoapRequest) -> CompletableFuture<CoapResponse>
```

Intercepting is achieved by implementing `Filter` interface, which is again a simple function:

```
(CoapRequest, Service<CoapRequest, CoapResponse>) -> CompletableFuture<CoapResponse>
```

That interface has helper functions to compose with another `Filter` and `Service`.

It is following server as a function idea, that is a very simple, flexible and testable way to model data processing.
It is best describe in this white paper: [Your Server as a Function](https://monkey.org/~marius/funsrv.pdf), and has a great implementation in [Finagle](https://twitter.github.io/finagle).

#### Routing service

Routing can be build with `RouterService.builder()` that creates a `Service` which routes incoming request into specific function based on uri path and method. For example:

```java
    Service<CoapRequest, CoapResponse> route = RouterService.builder()
        .get("/hello",req ->
            completedFuture(CoapResponse.of("Hello World", MediaTypes.CT_TEXT_PLAIN))
        )
        .build();
``` 

Note that each resource function is also with a `Service` type and can be decorated/transformed with `Filter`

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

### Creating a client

To make a CoAP request, use the class `CoapClient`. It uses fluent API. The following is a simple example on the usage:

    CoapClient client = CoapClientBuilder.newBuilder(new InetSocketAddress("localhost",5683)).build();
    
    CoapResponse resp = client.sendSync(get("/s/temp"));
    
    resp = client.sendSync(put("/a/relay").payload("1", MediaTypes.CT_TEXT_PLAIN));
        
    //it is important to close connection in order to release socket
    client.close();
    

Example client
--------------

This [example client](example-client) demonstrates how to build coap client.


Development
-----------

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
