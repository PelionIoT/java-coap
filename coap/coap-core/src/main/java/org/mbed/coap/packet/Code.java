/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

/**
 * @author szymon
 */
public enum Code {
    //RFC 7252
    //draft-ietf-core-http-mapping-08 - for HTTP mapping

    C201_CREATED(2, 01, 201),
    C202_DELETED(2, 02, 200),
    C203_VALID(2, 03, 200),
    C204_CHANGED(2, 04, 200),
    C205_CONTENT(2, 05, 200),
    C400_BAD_REQUEST(4, 00, 400),
    C401_UNAUTHORIZED(4, 01, 403),
    C402_BAD_OPTION(4, 02, 400),
    C403_FORBIDDEN(4, 03, 403),
    C404_NOT_FOUND(4, 04, 404),
    C405_METHOD_NOT_ALLOWED(4, 05, 400),
    C406_NOT_ACCEPTABLE(4, 06, 406),
    C412_PRECONDITION_FAILED(4, 12, 412),
    C415_UNSUPPORTED_MEDIA_TYPE(4, 15, 415),
    C500_INTERNAL_SERVER_ERROR(5, 00, 500),
    C501_NOT_IMPLEMENTED(5, 01, 501),
    C502_BAD_GATEWAY(5, 02, 502),
    C503_SERVICE_UNAVAILABLE(5, 03, 503),
    C504_GATEWAY_TIMEOUT(5, 04, 504),
    C505_PROXYING_NOT_SUPPORTED(5, 05, 502),
    //
    //draft-ietf-core-block-14
    C231_CONTINUE(2, 31, 500),
    C408_REQUEST_ENTITY_INCOMPLETE(4, 8, 500),
    C413_REQUEST_ENTITY_TOO_LARGE(4, 13, 413);
    private final int coapCode;
    private final int httpStatus;

    Code(int codeClass, int codeDetail, int httpStatus) {
        this.coapCode = (codeClass << 5) + codeDetail;
        this.httpStatus = httpStatus;
    }

    public int getHttpCode() {
        return httpStatus;
    }

    public int getCoapCode() {
        return coapCode;
    }

    public static Code valueOf(int code) {
        for (Code c : Code.values()) {
            if (c.getCoapCode() == code) {
                return c;
            }
        }
        return null;
    }

    public static Code valueOf(int codeClass, int codeDetail) {
        int coapCode = (codeClass << 5) + codeDetail;
        return valueOf(coapCode);
    }
}
