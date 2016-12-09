/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package org.mbed.coap.transport;

import java.util.Arrays;

/**
 * This class provides transport context information.
 *
 * Created by szymon
 */
public class TransportContext {

    private final Integer trafficClass;
    private final String certificateCN;
    private final byte[] preSharedKeyId;
    private final String msisdn;
    private final String issuerCN;
    public final static TransportContext NULL = new TransportContext(null, null, null, null, null);

    public TransportContext(Integer trafficClass, String certificateCN, byte[] preSharedKeyId, String msisdn, String issuerCN) {
        this.trafficClass = trafficClass;
        this.certificateCN = certificateCN;
        this.preSharedKeyId = preSharedKeyId;
        this.msisdn = msisdn;
        this.issuerCN = issuerCN;
    }

    public String getCertificateCN() {
        return certificateCN;
    }

    public String getIssuerCN() {
        return issuerCN;
    }

    public byte[] getPreSharedKeyID() {
        return preSharedKeyId;
    }

    public Integer getTrafficClass() {
        return trafficClass;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public TransportContext withCertificateCN(String certificateCN) {
        return new TransportContext(trafficClass, certificateCN, preSharedKeyId, msisdn, issuerCN);
    }

    public TransportContext withPreSharedKeyID(byte[] preSharedKeyId) {
        return new TransportContext(trafficClass, certificateCN, preSharedKeyId, msisdn, issuerCN);
    }

    public TransportContext withTrafficClass(Integer trafficClass) {
        return new TransportContext(trafficClass, certificateCN, preSharedKeyId, msisdn, issuerCN);
    }

    public TransportContext withMsisdn(String msisdn) {
        return new TransportContext(trafficClass, certificateCN, preSharedKeyId, msisdn, issuerCN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransportContext)) {
            return false;
        }

        TransportContext that = (TransportContext) o;

        if (certificateCN != null ? !certificateCN.equals(that.certificateCN) : that.certificateCN != null) {
            return false;
        }
        if (!Arrays.equals(preSharedKeyId, that.preSharedKeyId)) {
            return false;
        }
        if (trafficClass != null ? !trafficClass.equals(that.trafficClass) : that.trafficClass != null) {
            return false;
        }
        if (issuerCN != null ? !issuerCN.equals(that.issuerCN) : that.issuerCN != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = trafficClass != null ? trafficClass.hashCode() : 0;
        result = 31 * result + (certificateCN != null ? certificateCN.hashCode() : 0);
        result = 31 * result + (preSharedKeyId != null ? Arrays.hashCode(preSharedKeyId) : 0);
        result = 31 * result + (issuerCN != null ? issuerCN.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TransportContext [trafficClass=" + trafficClass + ", certificateCN=" + certificateCN + ", preSharedKeyId=" + Arrays.toString(preSharedKeyId) + ", msisdn=" + msisdn + ", issuerCn=" + issuerCN + "]";
    }

}
