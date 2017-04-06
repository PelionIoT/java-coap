mbed CoAP
=========

![status](https://circleci.com/gh/ARMmbed/java-coap.svg?style=svg&circle-token=06fa0ece2bf5072957eb5fda93ce5d2254e4ef2b)

Introduction
------------

Java implementation of CoAP protocol:
- The Constrained Application Protocol [RFC 7252](https://tools.ietf.org/html/rfc7252)
- Observing Resources in the Constrained Application Protocol [RFC 7641](https://tools.ietf.org/html/rfc7641)
- Block-Wise Transfers in the Constrained Application Protocol [RFC 7959](https://tools.ietf.org/html/rfc7959)
- Constrained RESTful Environments (CoRE) Link Format [RFC 6690](https://tools.ietf.org/html/rfc6690)



Usage
-----

Add dependency into `pom.xml`:

        <dependency>
            <groupId>com.mbed</groupId>
            <artifactId>coap-core</artifactId>
            <version>{VERSION}</version>
        </dependency>


Client example:

    TODO
    
    
Server example:
    
    TODO


Development
-----------

### Build

mvn clean install 

Contribution
------------

Please read this instructions before making any pull request: 
https://docs.mbed.com/docs/getting-started-mbed-os/en/latest/Full_Guide/contributing/#contributing-to-the-mbed-os-code-base

**Note**: If you publish a feature or a solution to a problem before signing the CLA, then find out that you are not able or allowed to sign the CLA, we will not be able to use your solution anymore. That may prevent us from solving the problem for you.

License
-------

Unless specifically indicated otherwise in a file, files are licensed under the Apache 2.0 license, 
as can be found in: [LICENSE](LICENSE)