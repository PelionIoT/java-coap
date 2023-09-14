/*
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
 * Copyright (c) 2023 Izuma Networks. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
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
package com.mbed.coap.server.internal;

/**
 * @author szymon
 */
public final class UriMatcher {
    private String uri = "";
    private final transient boolean isPrefix;

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
