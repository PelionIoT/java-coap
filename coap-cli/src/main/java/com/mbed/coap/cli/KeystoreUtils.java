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
package com.mbed.coap.cli;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class KeystoreUtils {

    private KeystoreUtils() {
    }

    public static char[] secret() {
        return "".toCharArray();
    }

    public static InetSocketAddress addressFromUri(URI uri) {
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    static KeyStore loadKeystore(String keystoreFile) {
        KeyStore ks = null;
        if (keystoreFile != null) {
            try (FileInputStream f = new FileInputStream(keystoreFile)) {
                ks = KeyStore.getInstance("JKS");
                ks.load(f, secret());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return ks;
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

    public static List<X509Certificate> readCAs(KeyStore ks) throws KeyStoreException {
        List<X509Certificate> certs = new LinkedList<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!ks.isKeyEntry(alias)) {
                certs.add((X509Certificate) ks.getCertificate(alias));
            }
        }

        return certs;
    }

}
