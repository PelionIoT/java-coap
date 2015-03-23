/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

/**
 *
 * @author szymon
 */
public enum Code {
    //RFC 7252

    C201_CREATED(2, 01),
    C202_DELETED(2, 02),
    C203_VALID(2, 03),
    C204_CHANGED(2, 04),
    C205_CONTENT(2, 05),
    C400_BAD_REQUEST(4, 00),
    C401_UNAUTHORIZED(4, 01),
    C402_BAD_OPTION(4, 02),
    C403_FORBIDDEN(4, 03),
    C404_NOT_FOUND(4, 04),
    C405_METHOD_NOT_ALLOWED(4, 05),
    C406_NOT_ACCEPTABLE(4, 06),
    C412_PRECONDITION_FAILED(4, 12),
    C415_UNSUPPORTED_MEDIA_TYPE(4, 15),
    C500_INTERNAL_SERVER_ERROR(5, 00),
    C501_NOT_IMPLEMENTED(5, 01),
    C502_BAD_GATEWAY(5, 02),
    C503_SERVICE_UNAVAILABLE(5, 03),
    C504_GATEWAY_TIMEOUT(5, 04),
    C505_PROXYING_NOT_SUPPORTED(5, 05),
    //
    //draft-ietf-core-block-14
    C231_CONTINUE(2, 31),
    C408_REQUEST_ENTITY_INCOMPLETE(4, 8),
    C413_REQUEST_ENTITY_TOO_LARGE(4, 13);
    private final int coapCode;

    private Code(int codeClass, int codeDetail) {
        this.coapCode = (codeClass << 5) + codeDetail;
    }

    public int getHttpCode() {
        try {
            return Integer.parseInt(name().substring(1, 4));
        } catch (NumberFormatException numberFormatException) {
            return 0;
        }
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
