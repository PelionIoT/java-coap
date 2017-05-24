Example CoAP client
===================

This is an example coap client application, that can be use with 
[connector.mbed.com](http://connector.mbed.com) or other LwM2M Server.

Usage
-----

    ./run.sh <registration-url> [<keystore-file>]

*For example:*
        
    ./run.sh 'coap://localhost:5683/rd?ep=device007&lt=3600&b=U'    


Integration with connector.mbed.com
-----------------------------------

### Create client's keystore

1. Login to [http://connector.mbed.com](http://connector.mbed.com)

2. From [https://connector.mbed.com/#credentials](https://connector.mbed.com/#credentials) click  `GET MY DEVICE SECURITY CREDENTIALS`
    
3. From generated file `security.h` copy:
   - `SERVER_CERT` into new file: `SERVER_CERT.pem`
   - `CERT` into new file: `CERT.pem`
   - `KEY` into new file `KEY.pem`
   
   Remove extra characters, like: `\r`, `\n`, `"`, `;` (but leave `-----xxxxx-----` lines). 
   File must looks like:
    
    ```
    -----BEGIN CERTIFICATE-----
    MIIBmDCCAT6gAwIBAgIEVUCA0jAKBggqhkjOPQQDAjBLMQswCQYDVQQGEwJGSTEN
    MAsGA1UEBwwET3VsdTEMMAoGA1UECgwDQVJNMQwwCgYDVQQLDANJb1QxETAPBgNV
    BAMMCEFSTSBtYmVkMB4XDTE1MDQyOTA2NTc0OFoXDTE4MDQyOTA2NTc0OFowSzEL
    MAkGA1UEBhMCRkkxDTALBgNVBAcMBE91bHUxDDAKBgNVBAoMA0FSTTEMMAoGA1UE
    CwwDSW9UMREwDwYDVQQDDAhBUk0gbWJlZDBZMBMGByqGSM49AgEGCCqGSM49AwEH
    A0IABLuAyLSk0mA3awgFR5mw2RHth47tRUO44q/RdzFZnLsAsd18Esxd5LCpcT9w
    0tvNfBv4xJxGw0wcYrPDDb8/rjujEDAOMAwGA1UdEwQFMAMBAf8wCgYIKoZIzj0E
    AwIDSAAwRQIhAPAonEAkwixlJiyYRQQWpXtkMZax+VlEiS201BG0PpAzAiBh2RsD
    NxLKWwf4O7D6JasGBYf9+ZLwl0iaRjTjytO+Kw==
    -----END CERTIFICATE-----
    ```   

4. Copy value of `MBED_ENDPOINT_NAME` for later use. It is the only valid endpoint name that can be use with new keystore.

5. Generate keystore with:

    ```
    openssl pkcs12 -export -in CERT.pem -inkey KEY.pem -out CERT_KEY.p12 -name device -password pass:secret
    keytool -keystore client.jks -storepass secret  -importkeystore -srcstorepass secret -srckeystore CERT_KEY.p12 -srcstoretype PKCS12 -noprompt
    keytool -keystore client.jks -storepass secret  -import -alias server-ca -file SERVER_CERT.pem -noprompt
    ```
    
    *This will create keystore file `client.jks` protected with password: `secret`.*


### Run

    ./run.sh 'coaps://api.connector.mbed.com:5684/rd?ep={MBED_ENDPOINT_NAME}' client.jks 
    
When successfully registered, logs should show for example:

    ...
    12:27:32 INFO  [EP:90ee124c-babe-4df9-807f-98c62be94945] Registered, lifetime: 3600s
    ...