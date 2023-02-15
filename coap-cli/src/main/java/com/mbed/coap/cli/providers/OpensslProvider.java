/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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

import static com.mbed.coap.cli.KeystoreUtils.findKeyAlias;
import static com.mbed.coap.cli.KeystoreUtils.secret;
import com.mbed.coap.cli.TransportProvider;
import com.mbed.coap.packet.Opaque;
import com.mbed.coap.transport.CoapTcpTransport;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.javassl.CoapSerializer;
import com.mbed.coap.transport.stdio.OpensslProcessTransport;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;

public class OpensslProvider implements TransportProvider {

    private final String cipherSuite;

    public OpensslProvider(String cipherSuite) {
        this.cipherSuite = cipherSuite != null ? cipherSuite : "ECDHE-ECDSA-AES128-SHA256";
    }

    @Override
    public CoapTcpTransport createTCP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks) throws GeneralSecurityException, IOException {
        return create(coapSerializer, destAdr, ks, false);
    }

    @Override
    public CoapTransport createUDP(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks, Pair<String, Opaque> psk) throws GeneralSecurityException, IOException {
        if (psk == null) {
            return create(coapSerializer, destAdr, ks, true);
        } else {
            return create(coapSerializer, destAdr, psk, true);
        }
    }

    private CoapTransport create(CoapSerializer coapSerializer, InetSocketAddress destAdr, Pair<String, Opaque> psk, Boolean isDtls) throws GeneralSecurityException, IOException {
        ProcessBuilder process = OpensslProcessTransport.createProcess(psk, destAdr, isDtls, cipherSuite);

        return new OpensslProcessTransport(process.start(), destAdr, coapSerializer);
    }

    private CoapTcpTransport create(CoapSerializer coapSerializer, InetSocketAddress destAdr, KeyStore ks, Boolean isDtls) throws GeneralSecurityException, IOException {
        String alias = findKeyAlias(ks);
        File temp = keyPairToTempFile(alias, ks);

        ProcessBuilder process = OpensslProcessTransport.createProcess(temp.getAbsolutePath(), destAdr, isDtls, cipherSuite);

        return new OpensslProcessTransport(process.start(), destAdr, coapSerializer);
    }

    private static File keyPairToTempFile(String alias, KeyStore ks) throws KeyStoreException, IOException, CertificateEncodingException, NoSuchAlgorithmException, UnrecoverableKeyException {
        File temp = File.createTempFile("client", ".pem");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(Base64.getEncoder().encodeToString(ks.getCertificate(alias).getEncoded()));
            writer.write("\n-----END CERTIFICATE-----\n");
            writer.write("-----BEGIN PRIVATE KEY-----\n");
            writer.write(Base64.getEncoder().encodeToString(ks.getKey(alias, secret()).getEncoded()));
            writer.write("\n-----END PRIVATE KEY-----\n");
            writer.flush();
        }
        temp.deleteOnExit();
        return temp;
    }


}
