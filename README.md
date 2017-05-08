mbed CoAP
=========

![CircleCI](https://img.shields.io/circleci/project/github/ARMmbed/java-coap/java7-backport.svg)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](LICENSE)
[![codecov](https://codecov.io/gh/ARMmbed/java-coap/branch/master/graph/badge.svg)](https://codecov.io/gh/ARMmbed/java-coap)
[![Known Vulnerabilities](https://snyk.io/test/github/armmbed/java-coap/badge.svg)](https://snyk.io/test/github/armmbed/java-coap)

---

:exclamation: **NOTE:** 
This is a Java 7 backport branch for [coap-core](coap-core). Generated artifacts are compatible with JRE 7. 
To minimize code difference with master, backporting is done with [retrolambda](https://github.com/orfjackal/retrolambda), [streamsupport](https://github.com/streamsupport/streamsupport) 
and [threetenbp](https://github.com/ThreeTen/threetenbp).

It will be updated with master branch by rebasing (`git rebase master`). 

----

Introduction
------------

This library makes it easy to integrate a Java SE enabled device with coap based services like [mbed Cloud](https://www.mbed.com/en/platform/cloud). 
It can also help to emulate an embedded device for prototyping and testing purposes. 

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
* LwM2M TLV and JSON data formats

Requirements
------------

### Runtime:

* JRE 8
* JRE 11

### Development:

* JDK 8
* maven 3.x


Using the Library
-----------------

Add repository to build file:

    <repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>

Add dependency into your `pom.xml`:

    <dependency>
        <groupId>com.mbed.java-coap</groupId>
        <artifactId>coap-core</artifactId>
        <version>{VERSION}</version>
    </dependency>


### Creating a Server

#### Initializing, starting and stopping the server

To initialize a server, you must at minimum define the port number. You must set the server parameters before starting a server. 

    CoapServer server = CoapServer.builder().transport(5683).build();
    server.start();

To stop a server, use the `stop()` method.
	
    server.stop();


#### Adding request handlers

You can add handlers before or while the server is running. There can be several URI paths assigned to the same handler. 
You can also remove a handler at any time.

    CoapHandler handler = new ReadOnlyCoapResource("24");
    server.addRequestHandler("/temperature", handler);
    
    server.removeRequestHandler(handler);



#### Creating CoAP resources

To create a CoAP resource, you must implement a `CoapHandler`. There is one abstract helper class `CoapResource` that can be extended. At minimum, implement the `get()` method. 

The following example overrides `get()` and `put()` and creates a simple CoAP resource:

    public class SimpleCoapResource extends CoapResource {
        private String body="Hello World";
        
        @Override
        public void get(CoapExchange ex) throws CoapCodeException {
            ex.setResponseBody("Hello World");
            ex.setResponseCode(Code.C205_CONTENT);
            ex.sendResponse();
        }
        
        @Override
        public void put(CoapExchange ex) throws CoapCodeException {
          body = ex.getRequestBodyString();        
            ex.setResponseCode(Code.C204_CHANGED);
            ex.sendResponse();
        }
    }

### Creating a client


To make a CoAP request, use the class `CoapClient`. It uses fluent API. The following is a simple example on the usage:

    CoapClient client = CoapClientBuilder.newBuilder(new InetSocketAddress("localhost",5683)).build();
    
    CoapPacket coapResp = client.resource("/s/temp").sync().get();
    
    coapResp = client.resource("/a/relay").payload("1", MediaTypes.CT_TEXT_PLAIN).sync().put();
        
    //it is important to close connection in order to release socket
    client.close();
    

Example client
--------------

This [example client](example-client) demonstrates how to build coap client.


Development
-----------

### Build

    mvn clean install
     
### Build with all checks enabled
     
    mvn clean install -P ci

### Update license header

    mvn com.mycila:license-maven-plugin:format


Contributions
-------------

All contributions are Apache 2.0. Only submit contributions where you have authored all of the code. If you do this on work time make sure your employer is OK with this.

License
-------

Unless specifically indicated otherwise in a file, files are licensed under the Apache 2.0 license, 
as can be found in: [LICENSE](LICENSE)
