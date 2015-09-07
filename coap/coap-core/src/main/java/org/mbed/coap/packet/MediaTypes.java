/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

import java.util.HashMap;
import java.util.Map;

/**
 * @author szymon
 */
public class MediaTypes {
    //RFC 7252

    public final static short CT_TEXT_PLAIN = 0;
    public final static short CT_APPLICATION_LINK__FORMAT = 40; //RFC 6690
    public final static short CT_APPLICATION_XML = 41;
    public final static short CT_APPLICATION_OCTET__STREAM = 42;
    public final static short CT_APPLICATION_EXI = 47;
    public final static short CT_APPLICATION_JSON = 50;
    //SenML
    public final static short CT_APPLICATION_SENML__JSON = 64;
    //TLV
    public final static short CT_APPLICATION_NANOSERVICE_TLV = 200;
    //--- OMA LwM2M ---
    //TODO: fix content-format values!
    /**
     * WARNING: content-format value is not verified! This string can contain a
     * character sequence, integer number, decimal number or any other sequence
     * of valid UTF-8 characters
     */
    public final static short CT_APPLICATION_LWM2M_TEXT = 97;
    /**
     * WARNING: content-format value is not verified! This data format is used
     * for binary Resources such as firmware images or application specific
     * binary formats
     */
    public final static short CT_APPLICATION_LWM2M_OPAQUE = 98;
    /**
     * WARNING: content-format value is not verified! The binary TLV
     * (Type-Length-Value) format is used to represent an array of values or a
     * singular value using a compact binary representation, which is easy to
     * process on simple embedded devices.
     */
    public final static short CT_APPLICATION_LWM2M_TLV = 99;
    /**
     * WARNING: content-format value is not verified!
     */
    public final static short CT_APPLICATION_LWM2M_JSON = 100;

    static final Map<Short, String> MEDIA_TYPE_MAP = new HashMap<>();

    static {
        MEDIA_TYPE_MAP.put(CT_TEXT_PLAIN, "text/plain");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_XML, "application/xml");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_OCTET__STREAM, "application/octet-stream");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_EXI, "application/exi");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_JSON, "application/json");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LINK__FORMAT, "application/link-format");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_SENML__JSON, "application/senml+json");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_NANOSERVICE_TLV, "application/nanoservice-tlv");
        //OMA LwM2M
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_TEXT, "application/vnd.oma.lwm2m+text");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_OPAQUE, "application/vnd.oma.lwm2m+opaque");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_TLV, "application/vnd.oma.lwm2m+tlv");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_JSON, "application/vnd.oma.lwm2m+json");
    }

    /**
     * Converts CoAP content type to HTML
     *
     * @param contentType content type
     * @return HTML content type or null if could not convert
     */
    public static String contentFormatToString(Short contentType) {
        if (contentType == null) {
            return null;
        }
        return MEDIA_TYPE_MAP.containsKey(contentType) ? MEDIA_TYPE_MAP.get(contentType) : null;
    }

    /**
     * Parses MIME content format to CoAP content format. If can not find
     * matching content type, null is returned.
     *
     * @param contentType MIME content type
     * @return CoAP content type
     */
    public static Short parseContentFormat(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (short ct : MEDIA_TYPE_MAP.keySet()) {
            //if (ct)
            if (MEDIA_TYPE_MAP.get(ct).equals(contentType)) {
                return ct;
            }
        }
        return null;
    }
}
