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
package com.mbed.coap.packet;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class that provides static helper methods for creating and parsing CoAP packet
 */
public final class DataConvertingUtility {

    private DataConvertingUtility() {
        //keep private
    }

    /**
     * Splits string with given character. Unlike String.split(..) this method
     * does not remove empty elements.
     *
     * @param val text to be split
     * @param ch splitting character
     */
    static String[] split(String val, char ch) {
        int offset = 0;
        ArrayList<String> list = new ArrayList<>();
        int nextPos = val.indexOf(ch, offset);

        while (nextPos != -1) {
            list.add(val.substring(offset, nextPos));
            offset = nextPos + 1;
            nextPos = val.indexOf(ch, offset);
        }
        if (offset == 0) {
            return new String[]{val};
        }

        list.add(val.substring(offset));
        return list.toArray(new String[0]);
    }

    public static Map<String, String> parseUriQuery(String uriQuery) throws ParseException {
        if (uriQuery == null || uriQuery.length() == 0) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] params = uriQuery.substring(uriQuery.indexOf('?') + 1).split("&");

        for (String prm : params) {
            String[] p = prm.split("=", 2);
            if (p.length != 2) {
                throw new ParseException("", 0);
            }
            result.put(p[0], p[1]);
        }
        return result;
    }

    public static Map<String, List<String>> parseUriQueryMult(String uriQuery) throws ParseException {
        if (uriQuery == null || uriQuery.length() == 0) {
            return null;
        }
        Map<String, List<String>> result = new HashMap<>();
        String[] params = uriQuery.substring(uriQuery.indexOf('?') + 1).split("&");

        for (String prm : params) {
            String[] p = prm.split("=", 2);
            if (p.length != 2) {
                throw new ParseException("", 0);
            }
            List<String> values = result.getOrDefault(p[0], new ArrayList<>(1));
            values.add(p[1]);
            result.put(p[0], values);
        }
        return result;
    }

    static Opaque[] extendOption(Opaque[] orig, Opaque extend) {
        if (orig == null || orig.length == 0) {
            return new Opaque[]{extend};
        } else {
            Opaque[] arr = new Opaque[orig.length + 1];
            System.arraycopy(orig, 0, arr, 0, orig.length);
            arr[orig.length] = extend;
            return arr;
        }
    }

    static String extendOption(String orig, Opaque extend, String delimiter, boolean startWithDelimiter) {
        String extOption = orig;
        if (extOption == null) {
            extOption = "";
        }
        if (extOption.length() == 0 && !startWithDelimiter) {
            return extOption + extend.toUtf8String();
        } else {
            return extOption + delimiter + extend.toUtf8String();
        }
    }

}
