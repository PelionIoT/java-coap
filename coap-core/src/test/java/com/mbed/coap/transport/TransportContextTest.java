/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;


public class TransportContextTest {

    private TransportContext.Key<String> DUMMY_KEY = new TransportContext.Key<>(null);
    private TransportContext.Key<String> DUMMY_KEY2 = new TransportContext.Key<>("na");

    @Test
    void test() {
        TransportContext trans = TransportContext.of(DUMMY_KEY, "perse");
        assertEquals("perse", trans.get(DUMMY_KEY));
        assertEquals("na", trans.get(DUMMY_KEY2));

        trans = trans.with(DUMMY_KEY2, "afds");
        assertEquals("perse", trans.get(DUMMY_KEY));
        assertEquals("afds", trans.get(DUMMY_KEY2));
    }

    @Test
    void empty() {
        TransportContext trans = TransportContext.EMPTY;
        assertNull(trans.get(DUMMY_KEY));
        assertEquals("default-val", trans.getOrDefault(DUMMY_KEY2, "default-val"));
        assertEquals("na", trans.get(DUMMY_KEY2));
    }

    @Test
    public void equalsAndHashTest() throws Exception {
        EqualsVerifier.forClass(TransportContext.class)
                .suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .withPrefabValues(TransportContext.class, TransportContext.EMPTY, TransportContext.of(TransportContext.NON_CONFIRMABLE, true))
                .verify();
    }

}
