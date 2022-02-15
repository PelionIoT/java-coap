/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.packet;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @author szymon
 */
public class MediaTypesTest {

    @Test
    public void should_return_null_for_unknown_content_format() {
        assertNull(MediaTypes.contentFormatToString(null));
        assertNull(MediaTypes.contentFormatToString((short) -2));
        assertNull(MediaTypes.contentFormatToString((short) 1));
        assertNull(MediaTypes.contentFormatToString((short) 9745));
        assertNull(MediaTypes.contentFormatToString((short) 164));

        assertNull(MediaTypes.parseContentFormat(null));
        assertNull(MediaTypes.parseContentFormat("non/existing"));
    }

    @Test
    public void contentTypeConverterTest() {
        assertEquals("text/plain", MediaTypes.contentFormatToString((short) 0));
        assertEquals("application/xml", MediaTypes.contentFormatToString((short) 41));
        assertEquals("application/octet-stream", MediaTypes.contentFormatToString((short) 42));
        assertEquals("application/exi", MediaTypes.contentFormatToString((short) 47));
        assertEquals("application/json", MediaTypes.contentFormatToString((short) 50));
        assertEquals("application/link-format", MediaTypes.contentFormatToString((short) 40));

        assertEquals("application/cbor", MediaTypes.contentFormatToString((short) 60));
        assertEquals("application/cose; cose-type=\"cose-mac\"", MediaTypes.contentFormatToString((short) 97));
        assertEquals("application/cose; cose-type=\"cose-sign1\"", MediaTypes.contentFormatToString((short) 18));
        assertEquals("application/coap-group+json", MediaTypes.contentFormatToString((short) 256));


        assertEquals((Short) MediaTypes.CT_TEXT_PLAIN, MediaTypes.parseContentFormat("text/plain"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_EXI, MediaTypes.parseContentFormat("application/exi"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_JSON, MediaTypes.parseContentFormat("application/json"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_LINK__FORMAT, MediaTypes.parseContentFormat("application/link-format"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_OCTET__STREAM, MediaTypes.parseContentFormat("application/octet-stream"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_XML, MediaTypes.parseContentFormat("application/xml"));

        assertEquals((Short) MediaTypes.CT_APPLICATION_CODE_ENCRYPT0, MediaTypes.parseContentFormat("application/cose; cose-type=\"cose-encrypt0\""));
        assertEquals((Short) MediaTypes.CT_APPLICATION_CODE_KEY, MediaTypes.parseContentFormat("application/cose-key"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_MERGE_PATCH_JSON, MediaTypes.parseContentFormat("application/merge-patch+json"));
    }
}
