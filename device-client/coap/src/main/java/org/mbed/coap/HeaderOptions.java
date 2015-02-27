/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.mbed.coap.exception.CoapMessageFormatException;
import org.mbed.coap.exception.CoapUnknownOptionException;
import org.mbed.coap.utils.HexArray;

/**
 * Implements CoAP basic header options.
 *
 * @author szymon
 */
@SuppressWarnings({"PMD.NPathComplexity", "PMD.CyclomaticComplexity"})
public class HeaderOptions implements Serializable {

    public static final byte IF_MATCH = 1; //multiple
    public static final byte URI_HOST = 3;
    public static final byte ETAG = 4;      //multiple in request
    public static final byte IF_NON_MATCH = 5;
    public static final byte URI_PORT = 7;
    public static final byte LOCATION_PATH = 8; //multiple
    public static final byte URI_PATH = 11;  //multiple
    public static final byte CONTENT_FORMAT = 12;
    public static final byte MAX_AGE = 14;
    public static final byte URI_QUERY = 15; //multiple
    public static final byte ACCEPT = 17;   //multiple
    public static final byte LOCATION_QUERY = 20; //multiple
    public static final byte PROXY_URI = 35; //not repeatable
    public static final byte PROXY_SCHEME = 39; //not repeatable
    private static final byte SIZE1 = 60;
    //
    public static final short DEFAULT_MAX_AGE = 60;
    public static final String DEFAULT_URI_HOST = "";
    //
    private Short contentFormat;
    private Long maxAge;
    private byte[][] etag; //opaque
    private String uriHost;
    private String locationPath;
    private String locationQuery;
    private String uriPath;
    private String uriQuery;
    private short[] accept;
    private byte[][] ifMatch;
    private Boolean ifNonMatch;
    private String proxyUri;
    private String proxyScheme;
    private Integer uriPort;
    private Integer size1;
    private Map<Integer, RawOption> unrecognizedOptions;

    protected boolean parseOption(int type, byte[] data) {
        switch (type) {
            case CONTENT_FORMAT:
                setContentFormat(readVariableULong(data).shortValue());
                break;
            case MAX_AGE:
                setMaxAge(readVariableULong(data));
                break;
            case ETAG:
                etag = extendOption(etag, data);
                break;
            case URI_HOST:
                setUriHost(CoapUtils.decodeString(data));
                break;
            case LOCATION_PATH:
                locationPath = extendOption(locationPath, data, "/", true);
                break;
            case LOCATION_QUERY:
                locationQuery = extendOption(locationQuery, data, "&", false);
                break;
            case URI_PATH:
                uriPath = extendOption(uriPath, data, "/", true);
                break;
            case URI_QUERY:
                uriQuery = extendOption(uriQuery, data, "&", false);
                break;
            case PROXY_URI:
                proxyUri = CoapUtils.decodeString(data);
                break;
            case PROXY_SCHEME:
                proxyScheme = CoapUtils.decodeString(data);
                break;
            case ACCEPT:
                accept = extendOption(accept, data);
                break;
            case IF_MATCH:
                ifMatch = extendOption(ifMatch, data);
                break;
            case IF_NON_MATCH:
                ifNonMatch = Boolean.TRUE;
                break;
            case URI_PORT:
                uriPort = readVariableULong(data).intValue();
                break;
            case SIZE1:
                size1 = readVariableULong(data).intValue();
                break;
            default:
                return false;
        }
        return true;
    }

    private static byte[][] extendOption(byte[][] orig, byte[] extend) {
        if (orig == null || orig.length == 0) {
            return new byte[][]{extend};
        } else {
            byte[][] arr = new byte[orig.length + 1][];
            System.arraycopy(orig, 0, arr, 0, orig.length);
            arr[orig.length] = extend;
            return arr;
        }
    }

    private static short[] extendOption(short[] orig, byte[] extend) {
        if (orig == null || orig.length == 0) {
            return new short[]{readVariableULong(extend).shortValue()};
        } else {
            short[] arr = new short[orig.length + 1];
            System.arraycopy(orig, 0, arr, 0, orig.length);
            arr[orig.length] = readVariableULong(extend).shortValue();
            return arr;
        }
    }

    private static String extendOption(String orig, byte[] extend, String delimiter, boolean startWithDelimiter) {
        String extOption = orig;
        if (extOption == null) {
            extOption = "";
        }
        if (extOption.length() == 0 && !startWithDelimiter) {
            return extOption + CoapUtils.decodeString(extend);
        } else {
            return extOption + delimiter + CoapUtils.decodeString(extend);
        }
    }

    /**
     * Returns value for given un-recognize option number.
     *
     * @param optNumber option number
     * @return byte array value or null if does not exist
     */
    public byte[] getCustomOption(Integer optNumber) {
        if (!unrecognizedOptions.containsKey(optNumber)) {
            return null;
        }
        return unrecognizedOptions.get(optNumber).getFirstValue();
    }

    /**
     * Tests for unknown critical options. If any of critical option is unknown
     * then throws CoapUnknownOptionException
     *
     * @throws CoapUnknownOptionException when critical option is unknown
     */
    public void criticalOptTest() throws CoapUnknownOptionException {
        if (unrecognizedOptions == null) {
            return;
        }
        for (int tp : unrecognizedOptions.keySet()) {
            if (isCritical(tp)) {
                throw new CoapUnknownOptionException(tp);
            }
        }
    }

    public static boolean isCritical(int optionNumber) {
        return (optionNumber & 1) != 0;
    }

    public static boolean isUnsave(int optionNumber) {
        return (optionNumber & 2) != 0;
    }

    public static boolean hasNoCacheKey(int optionNumber) {
        return (optionNumber & 0x1e) == 0x1c;
    }

    /**
     * Adds header option
     *
     * @param optionNumber option number
     * @param data option value as byte array
     * @return true if header type is a known, false for unknown header option
     */
    public final boolean put(int optionNumber, byte[] data) {
        if (parseOption(optionNumber, data)) {
            return true;
        }
        //unrecognizeg option header
        if (unrecognizedOptions == null) {
            unrecognizedOptions = new HashMap<>();
        }
        unrecognizedOptions.put(optionNumber, new RawOption(optionNumber, data));
        return true;
    }

    /**
     * Returns list with header options.
     *
     * @return sorted list
     */
    protected List<RawOption> getRawOptions() {
        LinkedList<RawOption> list = new LinkedList<>();

        if (contentFormat != null) {
            list.add(RawOption.fromUint(CONTENT_FORMAT, contentFormat.longValue()));
        }
        if (maxAge != null && maxAge != DEFAULT_MAX_AGE) {
            list.add(RawOption.fromUint(MAX_AGE, maxAge));
        }
        if (etag != null) {
            list.add(new RawOption(ETAG, etag));
        }
        if (uriHost != null && !uriHost.equals(DEFAULT_URI_HOST)) {
            list.add(RawOption.fromString(URI_HOST, uriHost));
        }
        if (locationPath != null) {
            list.add(RawOption.fromString(LOCATION_PATH, split(locationPath, '/')));
        }
        if (locationQuery != null) {
            list.add(RawOption.fromString(LOCATION_QUERY, locationQuery.split("&")));
        }
        if (uriPath != null && !uriPath.equals("/")) {
            list.add(RawOption.fromString(URI_PATH, split(uriPath, '/')));
        }
        if (uriQuery != null) {
            list.add(RawOption.fromString(URI_QUERY, uriQuery.split("&")));
        }
        if (proxyUri != null) {
            list.add(RawOption.fromString(PROXY_URI, proxyUri));
        }
        if (proxyScheme != null) {
            list.add(RawOption.fromString(PROXY_SCHEME, proxyScheme));
        }
        if (this.accept != null) {
            list.add(RawOption.fromUint(ACCEPT, accept));
        }
        if (this.uriPort != null) {
            list.add(RawOption.fromUint(URI_PORT, uriPort.longValue()));
        }
        if (ifNonMatch != null && ifNonMatch) {
            list.add(RawOption.fromEmpty(IF_NON_MATCH));
        }
        if (ifMatch != null) {
            list.add(new RawOption(IF_MATCH, ifMatch));
        }
        if (size1 != null) {
            list.add(RawOption.fromUint(SIZE1, size1.longValue()));
        }

        if (unrecognizedOptions != null) {
            for (RawOption rOpt : unrecognizedOptions.values()) {
                list.add(rOpt);
            }
        }
        return list;
    }

    /**
     * Returns number of header options. If greater that 14 then returns 15.
     *
     * @return option count
     */
    final byte getOptionCount() {
        int optCount = 0;
        for (RawOption rOpt : getRawOptions()) {
            optCount += rOpt.optValues.length == 0 ? 1 : rOpt.optValues.length;
        }
        return (byte) (optCount > 14 ? 15 : optCount);
    }

    private static byte[][] stringArrayToBytes(String[] strArray) {
        byte[][] bt;
        int skip = 0;
        if (strArray.length > 0 && strArray[0].length() == 0) {
            bt = new byte[strArray.length - 1][];
            skip = 1;
        } else {
            bt = new byte[strArray.length][];
        }
        for (int i = 0; i < bt.length; i++) {
            bt[i] = CoapUtils.encodeString(strArray[i + skip]);
        }
        return bt;
    }

    /**
     * Converts given byte array to Long number.
     *
     * @param data byte array
     * @return converted number
     */
    public static Long readVariableULong(byte[] data) {
        if (data == null) {
            return null;
        }
        Long val = Long.valueOf(0);
        for (byte b : data) {
            val <<= 8;
            val += (b & 0xFF);
        }
        return val;
    }

    protected static byte[][] writeVariableUInt(short[] value) {
        List<byte[]> btList = new ArrayList<>();

        for (int i = 0; i < value.length; i++) {
            //if (value[i] != null) {
            btList.add(writeVariableUInt(value[i], 1));
            //bt[i] = writeVariableUInt(value[i], 1);
            //} else {
            //    btList.add(new byte[0]);
            //}
        }
        return btList.toArray(new byte[btList.size()][1]);
    }

    /**
     * Converts given number into byte array.
     *
     * @param value number to convert
     * @return converted byte array
     */
    public static byte[] convertVariableUInt(Long value) {
        if (value == null) {
            return null;
        }
        return writeVariableUInt(value, 1);
    }

    public static byte[] convertVariableUInt(long value) {
        return writeVariableUInt(value, 1);
    }

    protected static byte[] writeVariableUInt(long value, int minBytes) {
        int len = 1;
        if (value > 0) {
            len = (int) Math.ceil((Math.log10(value + 1) / Math.log10(2)) / 8); //calculates needed minimum length
        }
        len = Math.max(len, minBytes);
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) (0xFF & (value >> 8 * (len - (i + 1))));
        }
        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    public void toString(StringBuilder sb) {
        if (uriPath != null) {
            sb.append(" URI:").append(uriPath);
        }
        if (uriQuery != null) {
            sb.append('?').append(uriQuery);
        }
        if (locationPath != null) {
            sb.append(" Loc:").append(locationPath);
            if (locationQuery != null) {
                sb.append('?').append(locationQuery);
            }
        } else if (locationQuery != null) {
            sb.append(" Loc:?").append(locationQuery);
        }
        if (etag != null && etag.length > 0) {
            sb.append(" ETag:").append(HexArray.toHex(etag[0]));
        }
        if (maxAge != null) {
            sb.append(" MaxAge:").append(maxAge).append('s');
        }
        if (contentFormat != null) {
            sb.append(" ContTp:").append(contentFormat);
        }
        if (uriHost != null) {
            sb.append(" Host:").append(uriHost);
        }
        if (unrecognizedOptions != null) {
            for (RawOption rOpt : unrecognizedOptions.values()) {
                if (rOpt.optValues.length > 0) {
                    sb.append(" H").append(rOpt.optNumber).append(":0x").append(HexArray.toHexShort(rOpt.optValues[0], 10));
                }
            }
        }
        if (proxyUri != null) {
            sb.append(" Proxy:");
            if (proxyScheme != null) {
                sb.append(proxyScheme).append("::/");
            }
            sb.append(proxyUri);
        }
        if (ifMatch != null && ifMatch.length > 0) {
            sb.append(" ifMatch:").append(HexArray.toHex(ifMatch[0]));
        }
        if (ifNonMatch != null && ifNonMatch) {
            sb.append(" ifNonMatch");
        }
        if (accept != null && accept.length > 0) {
            sb.append(" accept:").append(accept[0]);
        }
        if (size1 != null) {
            sb.append(" sz:").append(size1);
        }
    }

    /**
     * Returns content format.
     *
     * @return content format
     */
    public Short getContentFormat() {
        return contentFormat;
    }

    /**
     * Sets content format
     *
     * @param contentFormat content format
     */
    public void setContentFormat(Short contentFormat) {
        this.contentFormat = contentFormat;
    }

    /**
     * The Max-age Option indicates the maximum age of the resource for use in
     * cache control in seconds. The option value is represented as a variable
     * length unsigned integer between 8 and 32 bits. A default value of 60
     * seconds is assumed in the absence of this option.
     * <p>
     * When included in a request, Max-age indicates the maximum age of a cached
     * representation of that resource the client will accept. When included in
     * a response, Max-age indicates the maximum time the representation may be
     * cached before it MUST be discarded. This option MUST NOT occur more than
     * once in a header.
     * </p>
     *
     * @return max-age in seconds or null if absent
     */
    public final Long getMaxAge() {
        return maxAge;
    }

    /**
     * @return max-age in seconds
     */
    public final long getMaxAgeValue() {
        return maxAge != null ? maxAge : DEFAULT_MAX_AGE;
    }

    /**
     * The option value is an integer number of seconds between 0 and 2^32-1
     * inclusive (about 136.1 years). A default value of 60 seconds is assumed
     * in the absence of the option in a response.
     *
     * @param maxAge max-age to set in seconds
     */
    public final void setMaxAge(Long maxAge) {
        if (maxAge != null && maxAge > 0xFFFFFFFFL) {
            this.maxAge = maxAge | 0xFFFFFFFFL;
        } else {
            this.maxAge = maxAge;
        }
    }

    /**
     * @return first etag from array or null of array is empty
     */
    public final byte[] getEtag() {
        if (etag == null || etag.length == 0) {
            return null;
        }
        return etag[0];
    }

    /**
     * @param etag the etag to set
     */
    public final void setEtag(byte[] etag) {
        if (etag == null || etag.length == 0) {
            this.etag = null;
            return;
        }
        if (etag.length > 8) {
            throw new IllegalArgumentException("Wrong ETAG option value, should be in range 1-8");
        }
        this.etag = new byte[][]{etag};
    }

    public byte[][] getEtagArray() {
        return etag;
    }

    public void setEtag(byte[][] etag) {
        //test etag
        for (int i = 0; i < etag.length; i++) {
            if (etag[i].length == 0 || etag[i].length > 8) {
                throw new IllegalArgumentException("Wrong ETAG option value, should be in range 1-8");
            }
        }
        this.etag = etag;
    }

    /**
     * @return the uriAuthority
     */
    public final String getUriAuthority() {
        return uriHost;
    }

    /**
     * @param uriHost the uriHost to set
     */
    public final void setUriHost(String uriHost) {
        this.uriHost = uriHost;
    }

    public String getUriHost() {
        return uriHost;
    }

    /**
     * @return the location
     */
    public final String getLocationPath() {
        return locationPath;
    }

    /**
     * @param location the location to set
     */
    public final void setLocationPath(String location) {
        if (location != null && (location.equals(".") || location.equals(".."))) {
            throw new IllegalArgumentException("Illegal Location-Path: " + location);
        }
        if (location == null || location.isEmpty()) {
            this.locationPath = null;
        } else {
            this.locationPath = location;
        }
    }

    /**
     * @return the uriPath
     */
    public final String getUriPath() {
        return uriPath;
    }

    /**
     * Sets uri-path. If not null or empty it must start with '/' character.
     *
     * @param uriPath the uriPath to set
     */
    public final void setUriPath(String uriPath) {
        if (uriPath != null && (uriPath.length() == 0 || uriPath.equals("/"))) {
            this.uriPath = null;
        } else {
            if (uriPath != null && uriPath.charAt(0) != '/') {
                throw new IllegalArgumentException("Uri-path must start with '/' character.");
            }
            this.uriPath = uriPath;
        }
    }

    /**
     * @return the uriQuery
     */
    public String getUriQuery() {
        return uriQuery;
    }

    /**
     * @param uriQuery the uriQuery to set
     */
    public void setUriQuery(String uriQuery) {
        this.uriQuery = uriQuery;
    }

    public void setAccept(short[] accept) {
        if (accept == null || accept.length == 0) {
            this.accept = null;
        } else {
            this.accept = accept;
        }
    }

    public short[] getAccept() {
        return accept;
    }

    public byte[][] getIfMatch() {
        return ifMatch;
    }

    public void setIfMatch(byte[][] ifMatch) {
        this.ifMatch = ifMatch;
    }

    public Boolean getIfNonMatch() {
        return ifNonMatch;
    }

    public void setIfNonMatch(Boolean ifNonMatch) {
        this.ifNonMatch = ifNonMatch;
    }

    public String getLocationQuery() {
        return locationQuery;
    }

    public void setLocationQuery(String locationQuery) {
        this.locationQuery = locationQuery;
    }

    public void setProxyUri(String proxyUri) {
        this.proxyUri = proxyUri;
    }

    public String getProxyUri() {
        return proxyUri;
    }

    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    public String getProxyScheme() {
        return proxyScheme;
    }

    public Integer getUriPort() {
        return uriPort;
    }

    public void setUriPort(Integer uriPort) {
        this.uriPort = uriPort;
    }

    public Map<String, String> getUriQueryMap() throws ParseException {
        if (uriQuery == null) {
            return Collections.emptyMap();
        }
        return parseUriQuery(uriQuery);
    }

    public Integer getSize1() {
        return size1;
    }

    public void setSize1(Integer size) {
        this.size1 = size;
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
        //TODO: parse for multiple values
        if (uriQuery == null || uriQuery.length() == 0) {
            return new HashMap<>(); //empty map
            //return null;
        }
        Map<String, List<String>> result = new HashMap<>();
        String[] params = uriQuery.substring(uriQuery.indexOf('?') + 1).split("&");

        for (String prm : params) {
            String[] p = prm.split("=", 2);
            if (p.length != 2) {
                throw new ParseException("", 0);
            }
            List<String> values = new LinkedList<>();
            values.add(p[1]);
            result.put(p[0], values);
        }
        return result;
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

        list.add(val.substring(offset, val.length()));
        return list.toArray(new String[list.size()]);
    }

    void serialize(OutputStream os) throws IOException, CoapMessageFormatException {
        List<RawOption> list = getRawOptions();
        Collections.sort(list);

        int lastOptNumber = 0;
        for (RawOption opt : list) {
            for (byte[] optValue : opt.optValues) {
                int delta = opt.optNumber - lastOptNumber;
                lastOptNumber = opt.optNumber;
                if (delta > 0xFFFF + 269) {
                    throw new CoapMessageFormatException("Delta with size: " + delta + " is not supported [option number: " + opt.optNumber + "]");
                }
                int len = optValue.length;
                if (len > 0xFFFF + 269) {
                    throw new CoapMessageFormatException("Header size: " + len + " is not supported [option number: " + opt.optNumber + "]");
                }
                writeOptionHeader(delta, len, os);
                os.write(optValue);
            }
        }
    }

    private static void writeOptionHeader(int delta, int len, OutputStream os) throws IOException {
        //first byte
        int tempByte;
        if (delta <= 12) {
            tempByte = delta << 4;
        } else if (delta < 269) {
            tempByte = 13 << 4;
        } else {
            tempByte = 14 << 4;
        }
        if (len <= 12) {
            tempByte |= len;
        } else if (len < 269) {
            tempByte |= 13;
        } else {
            tempByte |= 14;
        }
        os.write(tempByte);

        //extended option delta
        if (delta > 12 && delta < 269) {
            os.write(delta - 13);
        } else if (delta >= 269) {
            os.write((0xFF00 & (delta - 269)) >> 8);
            os.write(0x00FF & (delta - 269));
        }
        //extended len
        if (len > 12 && len < 269) {
            os.write(len - 13);
        } else if (len >= 269) {
            os.write((0xFF00 & (len - 269)) >> 8);
            os.write(0x00FF & (len - 269));
        }
    }

    /**
     * De-serializes CoAP header options. Returns true if PayloadMarker was
     * found.
     */
    boolean deserialize(InputStream is) throws IOException, CoapMessageFormatException {

        int headerOptNum = 0;
        while (is.available() > 0) {
            int hdrByte = is.read();
            if (hdrByte == CoapPacket.PAYLOAD_MARKER) {
                return true;
            }
            int delta = hdrByte >> 4;
            int len = 0xF & hdrByte;

            if (delta == 15 || len == 15) {
                throw new CoapMessageFormatException("Unexpected delta or len value in option header");
            }
            if (delta == 13) {
                delta += is.read();
            } else if (delta == 14) {
                delta = is.read() << 8;
                delta += is.read();
                delta += 269;
            }
            if (len == 13) {
                len += is.read();
            } else if (len == 14) {
                len = is.read() << 8;
                len += is.read();
                len += 269;
            }
            headerOptNum += delta;
            byte[] headerOptData = new byte[len];
            is.read(headerOptData);
            put(headerOptNum, headerOptData);

        }
        //end of stream
        return false;

    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + (this.contentFormat != null ? this.contentFormat.hashCode() : 0);
        hash = 41 * hash + (this.maxAge != null ? this.maxAge.hashCode() : 0);
        hash = 41 * hash + Arrays.deepHashCode(this.etag);
        hash = 41 * hash + (this.uriHost != null ? this.uriHost.hashCode() : 0);
        hash = 41 * hash + (this.locationPath != null ? this.locationPath.hashCode() : 0);
        hash = 41 * hash + (this.locationQuery != null ? this.locationQuery.hashCode() : 0);
        hash = 41 * hash + (this.uriPath != null ? this.uriPath.hashCode() : 0);
        hash = 41 * hash + (this.uriQuery != null ? this.uriQuery.hashCode() : 0);
        hash = 41 * hash + Arrays.hashCode(this.accept);
        hash = 41 * hash + Arrays.deepHashCode(this.ifMatch);
        hash = 41 * hash + (this.ifNonMatch != null ? this.ifNonMatch.hashCode() : 0);
        hash = 41 * hash + (this.proxyUri != null ? this.proxyUri.hashCode() : 0);
        hash = 41 * hash + (this.proxyScheme != null ? this.proxyScheme.hashCode() : 0);
        hash = 41 * hash + (this.uriPort != null ? this.uriPort.hashCode() : 0);
        hash = 41 * hash + (this.size1 != null ? this.size1.hashCode() : 0);
        hash = 41 * hash + (this.unrecognizedOptions != null ? this.unrecognizedOptions.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HeaderOptions other = (HeaderOptions) obj;
        if (this.contentFormat != other.contentFormat && (this.contentFormat == null || !this.contentFormat.equals(other.contentFormat))) {
            return false;
        }
        if (this.maxAge != other.maxAge && (this.maxAge == null || !this.maxAge.equals(other.maxAge))) {
            return false;
        }
        if (!Arrays.deepEquals(this.etag, other.etag)) {
            return false;
        }
        if ((this.uriHost == null) ? (other.uriHost != null) : !this.uriHost.equals(other.uriHost)) {
            return false;
        }
        if ((this.locationPath == null) ? (other.locationPath != null) : !this.locationPath.equals(other.locationPath)) {
            return false;
        }
        if ((this.locationQuery == null) ? (other.locationQuery != null) : !this.locationQuery.equals(other.locationQuery)) {
            return false;
        }
        if ((this.uriPath == null) ? (other.uriPath != null) : !this.uriPath.equals(other.uriPath)) {
            return false;
        }
        if ((this.uriQuery == null) ? (other.uriQuery != null) : !this.uriQuery.equals(other.uriQuery)) {
            return false;
        }
        if (!Arrays.equals(this.accept, other.accept)) {
            return false;
        }
        if (!Arrays.deepEquals(this.ifMatch, other.ifMatch)) {
            return false;
        }
        if (this.ifNonMatch != other.ifNonMatch && (this.ifNonMatch == null || !this.ifNonMatch.equals(other.ifNonMatch))) {
            return false;
        }
        if ((this.proxyUri == null) ? (other.proxyUri != null) : !this.proxyUri.equals(other.proxyUri)) {
            return false;
        }
        if (this.uriPort != other.uriPort && (this.uriPort == null || !this.uriPort.equals(other.uriPort))) {
            return false;
        }
        if ((this.size1 == null) ? (other.size1 != null) : !this.size1.equals(other.size1)) {
            return false;
        }
        if (this.unrecognizedOptions != other.unrecognizedOptions && (this.unrecognizedOptions == null || !this.unrecognizedOptions.equals(other.unrecognizedOptions))) {
            return false;
        }
        return true;
    }

    protected static class RawOption implements Comparable<RawOption>, Serializable {

        int optNumber;
        byte[][] optValues;

        private static RawOption fromString(int num, String[] stringVal) {
            return new RawOption(num, stringArrayToBytes(stringVal));
        }

        public static RawOption fromUint(int num, Long uintVal) {
            return new RawOption(num, new byte[][]{convertVariableUInt(uintVal)});
        }

        public static RawOption fromString(int num, String stringVal) {
            return new RawOption(num, new byte[][]{CoapUtils.encodeString(stringVal)});
        }

        public static RawOption fromUint(int num, short[] uintVal) {
            return new RawOption(num, writeVariableUInt(uintVal));
        }

        public static RawOption fromEmpty(int num) {
            return new RawOption(num, new byte[][]{{}});
        }

        public RawOption(int optNumber, byte[][] optValues) {
            this.optNumber = optNumber;
            this.optValues = optValues;
        }

        public RawOption(int optNumber, byte[] singleOptValue) {
            this.optNumber = optNumber;
            this.optValues = new byte[1][];
            this.optValues[0] = singleOptValue;
        }

        public int getNumber() {
            return optNumber;
        }

        public byte[][] getValues() {
            return optValues;
        }

        public byte[] getFirstValue() {
            return (optValues.length > 0) ? optValues[0] : null;
        }

        @Override
        public int compareTo(RawOption o) {
            return (optNumber < o.optNumber ? -1 : (optNumber == o.optNumber ? 0 : 1));
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + this.optNumber;
            hash = 47 * hash + Arrays.deepHashCode(this.optValues);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RawOption other = (RawOption) obj;
            if (this.optNumber != other.optNumber) {
                return false;
            }
            if (!Arrays.deepEquals(this.optValues, other.optValues)) {
                return false;
            }
            return true;
        }
    }
}
