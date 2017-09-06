/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

/**
 * @author szymon
 */
public class MediaTypes {
    //https://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats

    //RFC 7252
    public final static short CT_TEXT_PLAIN = 0;
    public final static short CT_APPLICATION_LINK__FORMAT = 40; //RFC 6690
    public final static short CT_APPLICATION_XML = 41;
    public final static short CT_APPLICATION_OCTET__STREAM = 42;
    public final static short CT_APPLICATION_EXI = 47;
    public final static short CT_APPLICATION_JSON = 50;
    //--- OMA LwM2M ---
    public final static short CT_APPLICATION_LWM2M_TLV = 11542;
    public final static short CT_APPLICATION_LWM2M_JSON = 11543;
    //RFC8152
    public final static short CT_APPLICATION_CODE_ENCRYPT0 = 16;
    public final static short CT_APPLICATION_CODE_MAC0 = 17;
    public final static short CT_APPLICATION_CODE_SIGN1 = 18;
    public final static short CT_APPLICATION_CODE_ENCRYPT = 96;
    public final static short CT_APPLICATION_CODE_MAC = 97;
    public final static short CT_APPLICATION_CODE_SIGN = 98;
    public final static short CT_APPLICATION_CODE_KEY = 101;
    public final static short CT_APPLICATION_CODE_KEY_SET = 102;
    //RFC6902
    public final static short CT_APPLICATION_JSON_PATCH_JSON = 51;
    //RFC7396
    public final static short CT_APPLICATION_MERGE_PATCH_JSON = 52;
    //RFC7049
    public final static short CT_APPLICATION_CBOR = 60;
    //RFC7390
    public final static short CT_APPLICATION_COAP_GROUP_JSON = 256;


    static final Map<Short, String> MEDIA_TYPE_MAP = new HashMap<>();

    static {
        MEDIA_TYPE_MAP.put(CT_TEXT_PLAIN, "text/plain");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_XML, "application/xml");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_OCTET__STREAM, "application/octet-stream");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_EXI, "application/exi");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_JSON, "application/json");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LINK__FORMAT, "application/link-format");
        //OMA LwM2M
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_TLV, "application/vnd.oma.lwm2m+tlv");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_LWM2M_JSON, "application/vnd.oma.lwm2m+json");
        //RFC8152
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_ENCRYPT0, "application/cose; cose-type=\"cose-encrypt0\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_MAC0, "application/cose; cose-type=\"cose-mac0\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_SIGN1, "application/cose; cose-type=\"cose-sign1\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_ENCRYPT, "application/cose; cose-type=\"cose-encrypt\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_MAC, "application/cose; cose-type=\"cose-mac\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_SIGN, "application/cose; cose-type=\"cose-sign\"");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_KEY, "application/cose-key");
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CODE_KEY_SET, "application/cose-key-set");
        //RFC6902
        MEDIA_TYPE_MAP.put(CT_APPLICATION_JSON_PATCH_JSON, "application/json-patch+json");
        //RFC7396
        MEDIA_TYPE_MAP.put(CT_APPLICATION_MERGE_PATCH_JSON, "application/merge-patch+json");
        //RFC7049
        MEDIA_TYPE_MAP.put(CT_APPLICATION_CBOR, "application/cbor");
        //RFC7390
        MEDIA_TYPE_MAP.put(CT_APPLICATION_COAP_GROUP_JSON, "application/coap-group+json");

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
