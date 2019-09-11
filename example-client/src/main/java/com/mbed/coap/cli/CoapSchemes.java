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
package com.mbed.coap.cli;

import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.javassl.SSLSocketClientTransport;
import com.mbed.coap.transport.javassl.SocketClientTransport;
import com.mbed.coap.transport.stdio.OpensslProcessTransport;
import com.mbed.coap.transport.stdio.StreamBlockingTransport;
import com.mbed.coap.transport.udp.DatagramSocketTransport;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoapSchemes {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapSchemes.class);

    static final CoapSchemes INSTANCE;
    static final char[] SECRET = "secret".toCharArray();

    static {
        try {
            Class coapTransportBuilderClass = Class.forName(System.getProperty("coap.cli.CoapSchemes", "com.mbed.coap.cli.CoapSchemes"));
            INSTANCE = ((CoapSchemes) coapTransportBuilderClass.newInstance());

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public final CoapServerBuilder create(String keystoreFile, URI uri) {
        return create(loadKeystore(keystoreFile), uri);
    }

    public String supportedSchemas() {
        return "coap, coap+tcp, coaps+tcp, coaps+tcp-d2 (draft 2), \nopenssl-coap (requires installed openssl that supports dtls), \nstdio-coap (standard IO), stdio-coap+tcp";
    }

    protected CoapServerBuilder create(KeyStore ks, URI uri) {
        InetSocketAddress destAdr = new InetSocketAddress(uri.getHost(), uri.getPort());
        switch (uri.getScheme()) {
            case "coap":
                return coapOverUdp();

            case "coap+tcp":
                return coapOverTcp(destAdr);

            case "coaps+tcp":
                return coapOverTls(destAdr, ks);

            case "coaps+tcp-d2":
                return coapOverTlsDraft2(destAdr, ks);

            case "openssl-coap":
                return coapOverDtls(destAdr, ks);

            case "stdio-coap":
                return stdIoCoap(destAdr, CoapSerializer.UDP);

            case "stdio-coap+tcp":
                return stdIoCoap(destAdr, CoapSerializer.TCP);


            default:
                throw new IllegalArgumentException("Protocol not supported: " + uri.getScheme());
        }
    }


    private static KeyStore loadKeystore(String keystoreFile) {
        KeyStore ks = null;
        if (keystoreFile != null) {
            try (FileInputStream f = new FileInputStream(keystoreFile)) {
                ks = KeyStore.getInstance("JKS");
                ks.load(f, SECRET);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return ks;
    }

    private CoapServerBuilder coapOverTcp(InetSocketAddress destAdr) {
        return CoapServerBuilder.newBuilderForTcp().transport(
                new SocketClientTransport(destAdr, SocketFactory.getDefault(), CoapSerializer.TCP, true)
        );
    }

    private CoapServerBuilder coapOverTlsDraft2(InetSocketAddress destAdr, KeyStore ks) {
        SSLContext sslContext = sslContextFromKeystore(ks);
        return CoapServerBuilder.newBuilder().transport(
                new SSLSocketClientTransport(destAdr, sslContext.getSocketFactory(), CoapSerializer.UDP, true)
        );
    }

    private CoapServerBuilder coapOverTls(InetSocketAddress destAdr, KeyStore ks) {
        SSLContext sslContext = sslContextFromKeystore(ks);
        return CoapServerBuilder.newBuilderForTcp().transport(
                new SSLSocketClientTransport(destAdr, sslContext.getSocketFactory(), CoapSerializer.TCP, true)
        );
    }

    private CoapServerBuilder coapOverUdp() {
        return CoapServerBuilder.newBuilder().transport(new DatagramSocketTransport(0));
    }

    private CoapServerBuilder stdIoCoap(InetSocketAddress destAdr, CoapSerializer serializer) {
        return CoapServerBuilder.newBuilder().transport(StreamBlockingTransport.forStandardIO(destAdr, serializer));
    }

    protected static SSLContext sslContextFromKeystore(KeyStore ks) {
        try {

            final KeyManagerFactory kmf;
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, SECRET);
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CoapServerBuilder coapOverDtls(InetSocketAddress destAdr, KeyStore ks) {
        try {
            String alias = findKeyAlias(ks);
            File temp = keyPairToTempFile(alias, ks);

            OpensslProcessTransport transport = OpensslProcessTransport.create(temp.getAbsolutePath(), destAdr);
            return CoapServerBuilder.newBuilder().transport(transport);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }


    }

    private static File keyPairToTempFile(String alias, KeyStore ks) throws KeyStoreException, IOException, CertificateEncodingException, NoSuchAlgorithmException, UnrecoverableKeyException {
        File temp = File.createTempFile("client", ".pem");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(Base64.getEncoder().encodeToString(ks.getCertificate(alias).getEncoded()));
            writer.write("\n-----END CERTIFICATE-----\n");
            writer.write("-----BEGIN PRIVATE KEY-----\n");
            writer.write(Base64.getEncoder().encodeToString(ks.getKey(alias, SECRET).getEncoded()));
            writer.write("\n-----END PRIVATE KEY-----\n");
            writer.flush();
        }
        temp.deleteOnExit();
        return temp;
    }


    public static String findKeyAlias(KeyStore ks) throws KeyStoreException {
        ArrayList<String> aliases = Collections.list(ks.aliases());

        for (String alias : aliases) {
            if (ks.isKeyEntry(alias) && !"ca".equals(alias)) {
                return alias;
            }
        }
        return null;
    }

}
