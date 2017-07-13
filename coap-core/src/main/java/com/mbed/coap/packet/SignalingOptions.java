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

import com.mbed.coap.exception.CoapMessageFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements CoAP signaling options from draft-ietf-core-coap-tcp-tls-09.
 */
@SuppressWarnings({"PMD.CyclomaticComplexity"})
public class SignalingOptions extends AbstractOptions implements Serializable {

    private static final byte MAX_MESSAGE_SIZE = 2;     //7.01
    private static final byte BLOCK_WISE_TRANSFER = 4;  //7.01
    private static final byte CUSTODY = 2;              //7.02, 7.03
    private static final byte ALTERNATIVE_ADDRESS = 2;  //7.04 Repeatable
    private static final byte HOLD_OFF = 4;             //7.04
    private static final byte BAD_CSM_OPTION = 2;       //7.05

    private Integer maxMessageSize;
    private Boolean blockWiseTransfer;
    private Boolean custody;
    private List<String> alternativeAddresses; //TODO: Better type than string?
    private Integer holdOff;
    private Integer badCsmOption;

    private boolean parseOption(int type, byte[] data, Code code) {
        if (code == Code.C701_CSM && type == MAX_MESSAGE_SIZE) {
            setMaxMessageSize(DataConvertingUtility.readVariableULong(data).intValue());
        } else if (code == Code.C701_CSM && type == BLOCK_WISE_TRANSFER) {
            setBlockWiseTransfer(true);
        } else if ((code == Code.C702_PING || code == Code.C703_PONG) && type == CUSTODY) {
            setCustody(true);
        } else if (code == Code.C704_RELEASE && type == ALTERNATIVE_ADDRESS) {
            if (data.length < 1 || data.length > 255) {
                throw new IllegalArgumentException("Illegal Alternative-Address size: " + data.length);
            }
            if (alternativeAddresses == null) {
                alternativeAddresses = new ArrayList<>();
            }
            alternativeAddresses.add(DataConvertingUtility.decodeToString(data));

        } else if (code == Code.C704_RELEASE && type == HOLD_OFF) {
            setHoldOff(DataConvertingUtility.readVariableULong(data).intValue());
        } else if (code == Code.C705_ABORT && type == BAD_CSM_OPTION) {
            setBadCsmOption(DataConvertingUtility.readVariableULong(data).intValue());
        } else {
            return false;
        }
        return true;
    }

    @Override
    List<RawOption> getRawOptions() {
        List<RawOption> l = new LinkedList<>();

        if (maxMessageSize != null) {
            l.add(RawOption.fromUint(MAX_MESSAGE_SIZE, maxMessageSize.longValue()));
        }
        if (blockWiseTransfer != null && blockWiseTransfer) {
            l.add(RawOption.fromEmpty(BLOCK_WISE_TRANSFER));
        }
        if (custody != null && custody) {
            l.add(RawOption.fromEmpty(CUSTODY));
        }
        if (alternativeAddresses != null) {
            String[] addressArr = new String[alternativeAddresses.size()];
            for (int i = 0; i < alternativeAddresses.size(); i++) {
                addressArr[i] = alternativeAddresses.get(i);
            }

            l.add(RawOption.fromString(ALTERNATIVE_ADDRESS, addressArr));
        }
        if (holdOff != null) {
            l.add(RawOption.fromUint(HOLD_OFF, holdOff.longValue()));
        }
        if (badCsmOption != null) {
            l.add(RawOption.fromUint(BAD_CSM_OPTION, badCsmOption.longValue()));
        }
        return l;
    }

    /**
     * De-serializes CoAP signaling options.
     *
     * @param inputStream input stream to de-serialize from
     * @param code CoAP signaling code
     * @return true if if PayloadMarker was found
     */
    public boolean deserialize(InputStream inputStream, Code code) throws IOException, CoapMessageFormatException {

        int headerOptNum = 0;
        while (inputStream.available() > 0) {
            OptionMeta option = getOptionMeta(inputStream);
            if (option == null) {
                return true;
            }
            headerOptNum += option.delta;
            byte[] headerOptData = new byte[option.length];
            inputStream.read(headerOptData);
            put(headerOptNum, headerOptData, code);

        }
        //end of stream
        return false;

    }

    /**
     * Adds signaling option
     *
     * @param optionNumber option number
     * @param data option value as byte array
     * @param code CoAP signaling code
     * @return true if header type is a known, false for unknown header option
     */
    public final boolean put(int optionNumber, byte[] data, Code code) {
        if (parseOption(optionNumber, data, code)) {
            return true;
        }
        return putUnrecognized(optionNumber, data);

    }

    @Override
    void toString(StringBuilder sb) {
        if (maxMessageSize != null) {
            sb.append(" Max-Message-Size:").append(maxMessageSize);
        }
        if (blockWiseTransfer != null && blockWiseTransfer) {
            sb.append(" Block-Wise-Transfer");
        }
        if (custody != null && custody) {
            sb.append(" Custody");
        }
        if (alternativeAddresses != null) {
            sb.append(" Alternative-Addresses:[");
            for (int i = 0; i < alternativeAddresses.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(alternativeAddresses.get(i));
            }
            sb.append(']');
        }
        if (holdOff != null) {
            sb.append(" Hold-Off:").append(holdOff);
        }
        if (badCsmOption != null) {
            sb.append(" Bad-CSM-Option:").append(badCsmOption);
        }
    }

    public Integer getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(Integer maxMessageSize) {
        if (custody != null || alternativeAddresses != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.01 specific signaling option already set");
        }
        if (maxMessageSize != null && (maxMessageSize < 0 || maxMessageSize > 0xFFFF)) {
            throw new IllegalArgumentException("Illegal Max-Message-Size argument: " + maxMessageSize);
        }
        this.maxMessageSize = maxMessageSize;
    }

    public Boolean getBlockWiseTransfer() {
        return blockWiseTransfer;
    }

    public void setBlockWiseTransfer(Boolean blockWiseTransfer) {
        if (custody != null || alternativeAddresses != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.01 specific signaling option already set");
        }
        this.blockWiseTransfer = blockWiseTransfer;
    }

    public Boolean getCustody() {
        return custody;
    }

    public void setCustody(Boolean custody) {
        if (maxMessageSize != null || blockWiseTransfer != null || alternativeAddresses != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.02 or 7.03 specific signaling option already set");
        }
        this.custody = custody;
    }

    /**
     * @return comma separated list of alternative addresses as string
     */
    public List<String> getAlternativeAddresses() {
        return alternativeAddresses;
    }

    /**
     * Set alternative addresses
     *
     * @param alternativeAddresses comma separated list as string
     */
    public void setAlternativeAddresses(List<String> alternativeAddresses) {
        if (maxMessageSize != null || blockWiseTransfer != null || custody != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.04 specific signaling option already set");
        }

        this.alternativeAddresses = alternativeAddresses;
    }

    public Integer getHoldOff() {
        return holdOff;
    }

    public void setHoldOff(Integer holdOff) {
        if (maxMessageSize != null || blockWiseTransfer != null || custody != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.04 specific signaling option already set");
        }
        if (holdOff != null && (holdOff < 0 || holdOff > 0xFFF)) {
            throw new IllegalArgumentException("Illegal Hold-Off argument: " + holdOff);
        }
        this.holdOff = holdOff;
    }

    public Integer getBadCsmOption() {
        return badCsmOption;
    }

    public void setBadCsmOption(Integer badCsmOption) {
        if (maxMessageSize != null || blockWiseTransfer != null || custody != null || alternativeAddresses != null || holdOff != null) {
            throw new IllegalStateException("Other than 7.05 specific signaling option already set");
        }
        this.badCsmOption = badCsmOption;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        SignalingOptions that = (SignalingOptions) o;

        if (this.maxMessageSize != that.maxMessageSize && (this.maxMessageSize == null || !this.maxMessageSize.equals(that.maxMessageSize))) {
            return false;
        }
        if (this.blockWiseTransfer != that.blockWiseTransfer && (this.blockWiseTransfer == null || !this.blockWiseTransfer.equals(that.blockWiseTransfer))) {
            return false;
        }
        if (this.custody != that.custody && (this.custody == null || !this.custody.equals(that.custody))) {
            return false;
        }
        if (this.alternativeAddresses != that.alternativeAddresses && (this.alternativeAddresses == null || !this.alternativeAddresses.equals(that.alternativeAddresses))) {
            return false;
        }
        if (this.holdOff != that.holdOff && (this.holdOff == null || !this.holdOff.equals(that.holdOff))) {
            return false;
        }
        if (this.badCsmOption != that.badCsmOption && (this.badCsmOption == null || !this.badCsmOption.equals(that.badCsmOption))) {
            return false;
        }
        return true;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (maxMessageSize != null ? maxMessageSize.hashCode() : 0);
        result = 31 * result + (blockWiseTransfer != null ? blockWiseTransfer.hashCode() : 0);
        result = 31 * result + (custody != null ? custody.hashCode() : 0);
        result = 31 * result + (alternativeAddresses != null ? alternativeAddresses.hashCode() : 0);
        result = 31 * result + (holdOff != null ? holdOff.hashCode() : 0);
        result = 31 * result + (badCsmOption != null ? badCsmOption.hashCode() : 0);
        return result;
    }
}
