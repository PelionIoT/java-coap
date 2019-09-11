/**
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.cli.providers;

import static com.mbed.coap.cli.CoapSchemes.*;
import com.mbed.coap.cli.TransportProvider;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SSLSocketClientTransport;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdkProvider implements TransportProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdkProvider.class);

    public CoapTransport createTCP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) throws GeneralSecurityException {
        SSLContext sslContext = sslContextFromKeystore(ks);

        return new SSLSocketClientTransport(destAdr, sslContext.getSocketFactory(), coapSerializer, true);
    }

    public CoapTransport createUDP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) {
        throw new IllegalArgumentException("DTLS not supported by Jdk secure provider");
    }


    protected static SSLContext sslContextFromKeystore(KeyStore ks) throws GeneralSecurityException {
            final KeyManagerFactory kmf;
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, secret());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            //print all certificates subject
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                String certCN = ((X509Certificate) ks.getCertificate(alias)).getSubjectDN().toString();

                if (ks.isKeyEntry(alias)) {
                    LOGGER.info("Using certificate: " + certCN);
                } else {
                    LOGGER.info("Using trusted certificate: " + certCN);
                }
            }

            return sslContext;
    }

}
