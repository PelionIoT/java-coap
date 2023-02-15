Coap command line tool
======================

Usage
-----

```
Usage: coap [COMMAND]
Commands:
  send      Send CoAP requests
  register  Register to LwM2M server and simulate some simple resources
```

### Supported schemes

- `coap://` (RFC 7252)
- `coaps://` (RFC 7252)
- `coap+tcp://` (RFC 8323)
- `coaps+tcp://` (RFC 8323)

### Examples

- `./coap send GET coap://coap.me/.well-known/core`
- `./coap register 'coap://leshan.eclipseprojects.io:5683/rd?ep=device007&lt=3600&b=U'`

#### Running with openssl:

    coap register -k device01.jks -s openssl 'coaps://localhost:5684/rd?ep=device001'

_Note:_

This requires `openssl` version that supports dtls. Use environment variable `COAPCLI_OPENSSL` to point to specific location of `openssl` binary.

For example: `export COAPCLI_OPENSSL=/usr/local/opt/openssl@1.1/bin/openssl`

#### Running with standard io (bidirectional pipe):

    mkfifo fifo
    coap register -s stdio "coaps://127.0.0.1:5683/rd?ep=test&aid=dm&lt=60" < fifo | \
    socat -d -d - udp-sendto:localhost:5683 > fifo

or

    coap register -s stdio "coaps://127.0.0.1:5683/rd?ep=test&aid=dm&lt=60" < fifo | \
    openssl s_client -connect localhost:5684 -cipher PSK-AES128-CBC-SHA -psk 01010101010101010101010101010101 -psk_identity device-0000000001  -quiet > fifo

Build distribution (zip)
------------------------

    ./gradlew coap-cli:distZip
