/*
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 */
package com.mbed.coap.linkformat;

import java.io.Serializable;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements CoRE Link Format defined in RFC 6690
 *
 * @author szymon
 * @see <a
 * href="http://tools.ietf.org/rfc/rfc6690.txt">http://tools.ietf.org/rfc/rfc6690.txt</a>
 */
public class LinkFormat implements Serializable {

    private static final long serialVersionUID = 100003L;
    String uri;
    private final Map<String, Object> params = new HashMap<>();
    //
    //--- RFC 6690 ---
    static final String LINK_RESOURCE_TYPE = "rt";
    static final String LINK_INTERFACE_DESCRIPTION = "if";
    static final String LINK_MAXIMUM_SIZE = "sz";
    static final String LINK_RELATIONS = "rel";
    static final String LINK_ANCHOR = "anchor";
    static final String LINK_REV = "rev";
    static final String LINK_HREFLANG = "hreflang";
    static final String LINK_MEDIA = "media";
    static final String LINK_TITLE = "title";
    static final String LINK_TYPE = "type";
    //
    //--- RFC 7252 ---
    static final String LINK_CONTENT_TYPE = "ct";
    //--- draft-ietf-core-observe-08 ---
    static final String LINK_OBSERVABLE = "obs";
    //no ietf spec
    static final String LINK_AUTO_OBSERVABLE = "aobs";
    //draft-shelby-core-resource-directory-04
    static final String LINK_RESOURCE_INSTANCE = "ins";
    static final String LINK_EXPORT = "exp";

    static void parseParam(LinkFormat lf, String paramName, String paramValue) throws ParseException {
        if (paramName.isEmpty()) {
            throw new ParseException("Parameter name is empty", 0);
        }
        try {
            CharSequence val = null;
            if (paramValue != null) {
                if (paramValue.startsWith("\"") && paramValue.endsWith("\"")) {
                    val = paramValue.substring(1, paramValue.length() - 1);
                    if (val.toString().indexOf('"') >= 0) {
                        throw new ParseException("Unexpected character: '\"' in parameter value: " + paramValue, 0);
                    }
                } else {
                    val = new PToken(paramValue);
                }
            }
            parse(paramName, lf, val);
        } catch (NumberFormatException ex) {
            throw new ParseException(ex.getMessage(), 0);
        } catch (IllegalArgumentException ex) {
            throw new ParseException(ex.getMessage(), 0);
        } catch (ClassCastException ex) {
            throw new ParseException("Expected ptoken value (without quotes)", 0);
        } catch (NullPointerException ex) {     //NOPMD
            throw new ParseException("Expected value for parameter", 0);
        } catch (Exception ex) {
            throw new ParseException(ex.getMessage(), 0);
        }
    }

    private static void parse(String paramName, LinkFormat lf, CharSequence val) throws ParseException, NumberFormatException {
        if (paramName.equals(LINK_MAXIMUM_SIZE)) {
            lf.setMaximumSize(Integer.parseInt(val.toString()));
        } else if (paramName.equals(LINK_CONTENT_TYPE)) {
            lf.setContentType(Short.parseShort(val.toString().split(" ")[0]));
        } else if (paramName.equals(LINK_HREFLANG)) {
            lf.setHRefLang(((PToken) val).toString());
        } else if (paramName.equals(LINK_RELATIONS)) {
            lf.set(LINK_RELATIONS, ((String) val));
        } else if (paramName.equals(LINK_TITLE)) {
            lf.setTitle(((String) val).toString());
        } else if (paramName.equals(LINK_ANCHOR)) {
            lf.setAnchor(((String) val).toString());
        } else if (paramName.equals(LINK_REV)) {
            lf.set(LINK_REV, ((String) val).toString());
        } else if (paramName.equals(LINK_RESOURCE_TYPE)) {
            lf.set(LINK_RESOURCE_TYPE, ((String) val).toString());
        } else if (paramName.equals(LINK_INTERFACE_DESCRIPTION)) {
            lf.set(LINK_INTERFACE_DESCRIPTION, ((String) val).toString());
        } else if (paramName.equals(LINK_OBSERVABLE)) {
            if (val != null) {
                throw new ParseException("Parameter 'obs' is a flag, not expected value", 0);
            }
            lf.setObservable(Boolean.TRUE);
        } else if (paramName.equals(LINK_MEDIA)) {
            lf.setMedia(val.toString()); //expects quoted or ptoken
        } else if (paramName.equals(LINK_TYPE)) {
            lf.setType(val.toString()); //expects quoted or ptoken
        } else if (paramName.equals(LINK_RESOURCE_INSTANCE)) {
            lf.setResourceInstance(((String) val).toString());
        } else if (paramName.equals(LINK_EXPORT)) {
            if (val != null) {
                throw new ParseException("Parameter 'exp' is a flag, not expected value", 0);
            }
            lf.setExport(Boolean.TRUE);
        } else {
            //custom (link-extension)
            if (val == null) {
                lf.set(paramName, Boolean.TRUE);
            } else if (val instanceof PToken) {
                lf.set(paramName, (PToken) val);
            } else {
                lf.set(paramName, (String) val);
            }
        }
    }

    LinkFormat() {
        // nothing to initialize
    }

    public LinkFormat(String uri) {
        this.uri = uri;
    }

    /**
     * Sets custom link attribute with text value
     *
     * @param parameterName parameter name
     * @param parameterValue parameter value
     */
    public void set(String parameterName, String parameterValue) {
        if (parameterName == null) {
            return;
        }
        params.put(parameterName, parameterValue);
    }

    /**
     * Sets link parameter with its value.
     *
     * @param parameterName link parameter name
     * @param parameterValue value as ptoken
     */
    public void set(String parameterName, PToken parameterValue) {
        if (parameterName == null) {
            return;
        }
        params.put(parameterName, parameterValue);
    }

    /**
     * Sets custom link attribute with number value
     *
     * @param parameterName parameter name
     * @param parameterValue value
     */
    public void set(String parameterName, Integer parameterValue) {
        if (parameterName == null || parameterValue == null) {
            return;
        }
        params.put(parameterName, parameterValue);
    }

    /**
     * Sets custom link attribute with boolean type of value
     *
     * @param parameterName parameter name
     * @param parameterValue value
     */
    public void set(String parameterName, Boolean parameterValue) {
        if (parameterName == null || parameterValue == null) {
            return;
        }
        params.put(parameterName, parameterValue);
    }

    /**
     * Sets link parameter with value of relation types.
     *
     * @param parameterName parameter name
     * @param parameterValue array of values
     * @throws IllegalArgumentException when contains illegal characters
     */
    public void set(String parameterName, String... parameterValue) throws IllegalArgumentException {
        if (parameterName == null) {
            return;
        }
        if (parameterValue == null || parameterValue.length == 0 || parameterValue[0] == null) {
            params.put(parameterName, null);
            return;
        }
        StringBuilder relValue = new StringBuilder();
        for (String rel : parameterValue) {
            if (relValue.length() > 0) {
                relValue.append(' ');
            }
            if (rel.contains(" ")) {
                throw new IllegalArgumentException("Space character is illegal in Relation link");
            }
            relValue.append(rel);
        }
        params.put(parameterName, relValue.toString());
    }

    /**
     * Sets resource types parameter.
     *
     * @param resourceTypes array with resource type values
     * @throws IllegalArgumentException when contains illegal characters
     */
    public void setResourceType(String... resourceTypes) throws IllegalArgumentException {
        this.set(LINK_RESOURCE_TYPE, resourceTypes);
    }

    public void setMaximumSize(Integer val) {
        params.put(LINK_MAXIMUM_SIZE, val);
    }

    public Integer getMaximumSize() {
        return getParamInt(LINK_MAXIMUM_SIZE);
    }

    public void setContentType(Short val) {
        params.put(LINK_CONTENT_TYPE, val);
    }

    /**
     * Sets interface description parameter.
     *
     * @param ifArr array of interface descriptions
     * @throws IllegalArgumentException when contains illegal characters
     */
    public void setInterfaceDescription(String... ifArr) throws IllegalArgumentException {
        this.set(LINK_INTERFACE_DESCRIPTION, ifArr);
    }

    public void setObservable(Boolean val) {
        params.put(LINK_OBSERVABLE, val);
    }

    public void setOAutobservable(Boolean val) {
        params.put(LINK_AUTO_OBSERVABLE, val);
    }

    public Boolean getObservable() {
        return getParamBoolean(LINK_OBSERVABLE);
    }

    public void setResourceInstance(String val) {
        params.put(LINK_RESOURCE_INSTANCE, val);
    }

    public void setExport(Boolean val) {
        params.put(LINK_EXPORT, val);
    }

    /**
     * Sets link relations
     *
     * @param relations array with relations data
     * @throws IllegalArgumentException when contains illegal characters
     */
    public void setRelations(String... relations) throws IllegalArgumentException {
        this.set(LINK_RELATIONS, relations);
    }

    /**
     * Returns array with link relations
     *
     * @return array with link relations or null if does not exist
     */
    public String[] getRelations() {
        return this.getParamRelationTypes(LINK_RELATIONS);
    }

    /**
     * Sets anchor parameter. This is used to describe a relationship between
     * two resources.
     *
     * @param anchor anchor parameter value
     */
    public void setAnchor(String anchor) {
        this.set(LINK_ANCHOR, anchor);
    }

    /**
     * Returns anchor parameter
     *
     * @return anchor parameter value or null if does not exist
     */
    public String getAnchor() {
        return this.getParam(LINK_ANCHOR);
    }

    /**
     * Sets rev parameter.
     *
     * @param revArr rev parameter value
     * @throws IllegalArgumentException when contains illegal characters
     */
    public void setRev(String... revArr) throws IllegalArgumentException {
        this.set(LINK_REV, revArr);
    }

    public String[] getRev() {
        return this.getParamRelationTypes(LINK_REV);
    }

    public void setHRefLang(String hreflangValue) {
        this.set(LINK_HREFLANG, new PToken(hreflangValue));
    }

    public String getHRefLang() {
        return this.getParam(LINK_HREFLANG);
    }

    public void setMedia(String media) {
        this.set(LINK_MEDIA, media);
    }

    public String getMedia() {
        return this.getParam(LINK_MEDIA);
    }

    public void setTitle(String title) {
        this.set(LINK_TITLE, title);
    }

    public String getTitle() {
        return this.getParam(LINK_TITLE);
    }

    public void setType(String type) {
        this.set(LINK_TYPE, type);
    }

    public String getType() {
        return this.getParam(LINK_TYPE);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    void toString(StringBuilder sb) {
        sb.append('<').append(uri).append('>');
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            sb.append(';').append(entry.getKey());
            if (val instanceof Integer) {
                sb.append('=').append(val.toString());
            } else if (val instanceof PToken) {
                sb.append('=').append(val);
            } else if (!(val instanceof Boolean)) {
                sb.append("=\"").append(val.toString()).append('\"');
            }
        }
    }

    public String getParam(String name) {
        if (params.get(name) != null) {
            return params.get(name).toString();
        }
        if ("href".equals(name)) {
            return uri;
        }
        return null;
    }

    private Short getParamShort(String name) {
        Object val = params.get(name);
        if (val != null) {
            if (val instanceof Short) {
                return (Short) val;
            }
            try {
                return Short.parseShort(val.toString().split(" ")[0]);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Integer getParamInt(String name) {
        Object val = params.get(name);
        if (val instanceof Integer) {
            return (Integer) val;
        }
        return null;
    }

    private boolean getParamBoolean(String name) {
        return params.get(name) != null;
    }

    public String[] getParamRelationTypes(String paramName) {
        if (params.get(paramName) == null) {
            return null;
        }
        String rel = params.get(paramName).toString();
        return rel.split(" ");
    }

    /**
     * @return the uri
     */
    public String getUri() {
        return uri;
    }

    public boolean isObservable() {
        return getParamBoolean(LINK_OBSERVABLE);
        //        Object val = params.get(LINK_OBSERVABLE);
        //        if (val != null && val instanceof Boolean) {
        //            return (Boolean) val;
        //        }
        //        return false;
    }

    /**
     * Interface description 'if' attribute.
     *
     * <p>
     * The interface description attribute is used to provide a name, URI or URN
     * indicating a specific interface definition used to interact with the
     * target resource. One can think of this as describing verbs usable on a
     * resource. The interface description attribute is meant to describe the
     * generic REST interface to interact with a resource or a set of resources.
     * </p>
     *
     * @return array with interface description
     */
    public String[] getInterfaceDescriptionArray() {
        return getParamRelationTypes(LINK_INTERFACE_DESCRIPTION);
    }

    /**
     * Returns first occurrence of interface description or null if is empty.
     *
     * @return interface description or null if does not exist
     */
    public String getInterfaceDescription() {
        String[] ifArr = getInterfaceDescriptionArray();
        return ifArr != null && ifArr.length > 0 ? ifArr[0] : null;
    }

    /**
     * Returns the Resource Type 'rt' attribute. It is used to assign an
     * application-specific semantic type to a resource. Multiple Resource Types
     * MAY be included in the value of this parameter
     * <p>
     * The Resource Type attribute is not meant to be used to assign a
     * human-readable name to a resource. The "title" attribute defined in
     * [RFC5988] is meant for that purpose.
     * </p>
     *
     * @return array with resource types
     */
    public String[] getResourceTypeArray() {
        return getParamRelationTypes(LINK_RESOURCE_TYPE);
    }

    /**
     * Returns first occurrence of resource type or null if is empty.
     *
     * @return resource type or null if does not exist
     */
    public String getResourceType() {
        String[] rtArr = getResourceTypeArray();
        return rtArr != null && rtArr.length > 0 ? rtArr[0] : null;
    }

    /**
     * Content-type code 'ct' attribute
     * <p>
     * The Content-type code "ct" attribute provides a hint about the Internet
     * media type this resource returns. Note that this is only a hint, and does
     * not override the Content-type Option of a CoAP response obtained by
     * actually following the link. The value is in the CoAP identifier code
     * format as a decimal ASCII integer [I-D.ietf-core-coap]. For example
     * application/xml would be indicated as "ct=41". If no Content-type code
     * attribute is present then nothing about the type can be assumed. The
     * Content-type code attribute MUST NOT appear more than once in a link.
     * </p>
     * Alternatively, the "type" attribute MAY be used to indicate an Internet
     * media type as a quoted-string [RFC5988]. It is not however expected that
     * constrained implementations are able to parse quoted- string Content-type
     * values. A link MAY include either a ct attribute or a type attribute, but
     * MUST NOT include both.
     *
     * @return Content type
     */
    public Short getContentType() {
        return getParamShort(LINK_CONTENT_TYPE);
    }

    /**
     * Maximum size estimate 'sz' attribute
     * <p>
     * The maximum size estimate attribute "sz" gives an indication of the
     * maximum size of the resource indicated by the target URI. This attribute
     * is not expected to be included for small resources that can comfortably
     * by carried in a single Maximum Transmission Unit (MTU), but SHOULD be
     * included for resources larger than that. The maximum size estimate
     * attribute MUST NOT appear more than once in a link.
     * </p>
     *
     * @return Maximum size estimate
     */
    public Integer getMaxSize() {
        return getParamInt(LINK_MAXIMUM_SIZE);
    }

    public String getResourceInstance() {
        return getParam(LINK_RESOURCE_INSTANCE);
    }

    public String getExport() {
        return getParam(LINK_EXPORT);
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.uri != null ? this.uri.hashCode() : 0);
        hash = 97 * hash + (this.params != null ? this.params.hashCode() : 0);
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
        final LinkFormat other = (LinkFormat) obj;
        if ((this.uri == null) ? (other.uri != null) : !this.uri.equals(other.uri)) {
            return false;
        }
        if (this.params != other.params && (this.params == null || !this.params.equals(other.params))) {
            return false;
        }
        return true;
    }
}
