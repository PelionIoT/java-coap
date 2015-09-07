/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.server.internal;

/**
 * @author szymon
 */
public final class UriMatcher {
    private String uri = "";
    private final boolean isPrefix;

    public UriMatcher(String uri) {
        if (uri.endsWith("*")) {
            isPrefix = true;
            this.uri = uri.substring(0, uri.length() - 1);
        } else {
            isPrefix = false;
            this.uri = uri;
        }
    }

    public boolean isMatching(String uriPath) {
        if (isPrefix) {
            return uriPath.startsWith(uri);
        } else {
            return uri.equals(uriPath);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UriMatcher other = (UriMatcher) obj;
        if ((this.uri == null) ? (other.uri != null) : !this.uri.equals(other.uri)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + (this.uri != null ? this.uri.hashCode() : 0);
        return hash;
    }

    public String getUri() {
        return uri;
    }

}
