Example CoAP client
===================

This is an example coap client application, that can be use with any LwM2M Server.

Usage
-----

    ./run.sh [-k KEYSTORE_FILE] [-s SSL_PROVIDER] <scheme>://<registration-url>

### Schemes

- `coap://` (RFC 7252)
- `coaps://` (RFC 7252)
- `coap+tcp://` (RFC 8323)
- `coaps+tcp://` (RFC 8323)

### Examples
        
    ./run.sh 'coap://localhost:5683/rd?ep=device007&lt=3600&b=U'    


#### Running with openssl:

    ./run.sh -k device01.jks -s openssl 'coaps://localhost:5684/rd?ep=device001'
    
_Note: this requires openssl installed in your system that support dtls. 
Use environment variable `OPENSSL_BIN_PATH` to point to specific location of `openssl` binary._


#### Running with standard io (bidirectional pipe):

    mkfifo fifo
    ./run.sh -s stdio "coaps://127.0.0.1:5683/rd?ep=test&aid=dm&lt=60" < fifo | \
    socat -d -d - udp-sendto:localhost:5683 > fifo
    
or

    ./run.sh -s stdio "coaps://127.0.0.1:5683/rd?ep=test&aid=dm&lt=60" < fifo | \
    openssl s_client -connect localhost:5684 -cipher PSK-AES128-CBC-SHA -psk 01010101010101010101010101010101 -psk_identity device-0000000001  -quiet > fifo
