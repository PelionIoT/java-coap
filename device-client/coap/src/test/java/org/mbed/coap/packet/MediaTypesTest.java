/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author szymon
 */
public class MediaTypesTest {

    @Test
    public void contentTypeConverterTest() {
        assertEquals("text/plain", MediaTypes.contentFormatToString((short) 0));
        assertEquals("application/xml", MediaTypes.contentFormatToString((short) 41));
        assertEquals("application/octet-stream", MediaTypes.contentFormatToString((short) 42));
        assertEquals("application/exi", MediaTypes.contentFormatToString((short) 47));
        assertEquals("application/json", MediaTypes.contentFormatToString((short) 50));
        assertEquals("application/link-format", MediaTypes.contentFormatToString((short) 40));
        assertEquals("application/senml+json", MediaTypes.contentFormatToString((short) 64));

        assertNull(MediaTypes.contentFormatToString(null));
        assertNull(MediaTypes.contentFormatToString((short) -2));
        assertNull(MediaTypes.contentFormatToString((short) 1));
        assertNull(MediaTypes.contentFormatToString((short) 9745));
        assertNull(MediaTypes.contentFormatToString((short) 164));

        assertEquals((Short) MediaTypes.CT_TEXT_PLAIN, MediaTypes.parseContentFormat("text/plain"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_EXI, MediaTypes.parseContentFormat("application/exi"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_JSON, MediaTypes.parseContentFormat("application/json"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_LINK__FORMAT, MediaTypes.parseContentFormat("application/link-format"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_OCTET__STREAM, MediaTypes.parseContentFormat("application/octet-stream"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_XML, MediaTypes.parseContentFormat("application/xml"));
        assertEquals((Short) MediaTypes.CT_APPLICATION_SENML__JSON, MediaTypes.parseContentFormat("application/senml+json"));

        assertNull(MediaTypes.parseContentFormat(null));
        assertNull(MediaTypes.parseContentFormat("non/existing"));
    }
}
