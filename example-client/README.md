Example CoAP client
===================

This is an example coap client application, that can be use with any LwM2M Server.

Usage
-----

    ./run.sh [-k KEYSTORE_FILE] <registration-url>

*For example:*
        
    ./run.sh 'coap://localhost:5683/rd?ep=device007&lt=3600&b=U'    
