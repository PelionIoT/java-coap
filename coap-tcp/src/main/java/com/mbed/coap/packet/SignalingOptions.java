/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
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

import static com.mbed.coap.utils.Validations.*;

/**
 * Implements CoAP signaling options from draft-ietf-core-coap-tcp-tls-09.
 */
@SuppressWarnings({"PMD.CyclomaticComplexity"})
public class SignalingOptions {

    private static final byte MAX_MESSAGE_SIZE = 2;     //7.01
    private static final byte BLOCK_WISE_TRANSFER = 4;  //7.01
    private static final byte CUSTODY = 2;              //7.02, 7.03
    private static final byte ALTERNATIVE_ADDRESS = 2;  //7.04 Repeatable
    private static final byte HOLD_OFF = 4;             //7.04
    private static final byte BAD_CSM_OPTION = 2;       //7.05

    private Long maxMessageSize; // uint32 does not fit into Integer, Integer is signed
    private Boolean blockWiseTransfer;
    private Boolean custody;
    //    private List<String> alternativeAddresses; //TODO: Better type than string?
    private String alternativeAddress;
    private Integer holdOff;
    private Integer badCsmOption;

    public static SignalingOptions capabilities(int maxMessageSize, boolean useBlockwiseTransfer) {
        SignalingOptions signalingOptions = new SignalingOptions();
        signalingOptions.setBlockWiseTransfer(useBlockwiseTransfer);
        signalingOptions.setMaxMessageSize(maxMessageSize);
        return signalingOptions;
    }

    SignalingOptions parse(int type, Opaque data, Code code) {
        if (code == Code.C701_CSM && type == MAX_MESSAGE_SIZE) {
            setMaxMessageSize(data.toLong());
        } else if (code == Code.C701_CSM && type == BLOCK_WISE_TRANSFER) {
            setBlockWiseTransfer(true);
        } else if ((code == Code.C702_PING || code == Code.C703_PONG) && type == CUSTODY) {
            setCustody(true);
        } else if (code == Code.C704_RELEASE && type == ALTERNATIVE_ADDRESS) {
            assume(data.size() >= 1 && data.size() <= 255, "Illegal Alternative-Address size: " + data.size());
            alternativeAddress = data.toUtf8String();

        } else if (code == Code.C704_RELEASE && type == HOLD_OFF) {
            setHoldOff(data.toInt());
        } else if (code == Code.C705_ABORT && type == BAD_CSM_OPTION) {
            setBadCsmOption(data.toInt());
        }
        return this;
    }

    Opaque serializeOption2() {

        if (maxMessageSize != null) {
            return Opaque.variableUInt(maxMessageSize);
        }
        if (custody != null && custody) {
            return Opaque.EMPTY;
        }

        if (alternativeAddress != null) {
            return Opaque.of(alternativeAddress);
        }
        if (badCsmOption != null) {
            return Opaque.variableUInt(badCsmOption.longValue());
        }
        return null;
    }

    Opaque serializeOption4() {
        if (blockWiseTransfer != null && blockWiseTransfer) {
            return Opaque.EMPTY;
        }

        if (holdOff != null) {
            return Opaque.variableUInt(holdOff.longValue());
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        if (maxMessageSize != null) {
            sb.append(" MaxMsgSz:").append(maxMessageSize);
        }
        if (blockWiseTransfer != null && blockWiseTransfer) {
            sb.append(" Blocks");
        }
        if (custody != null && custody) {
            sb.append(" Custody");
        }
        if (alternativeAddress != null) {
            sb.append(" AltAdr:");
            sb.append(alternativeAddress);
        }
        if (holdOff != null) {
            sb.append(" Hold-Off:").append(holdOff);
        }
        if (badCsmOption != null) {
            sb.append(" Bad-CSM:").append(badCsmOption);
        }
        return sb.toString();
    }

    public Long getMaxMessageSize() {
        return maxMessageSize;
    }

    // just helper when max size < Integer.MAX_VALUE
    public void setMaxMessageSize(int maxMessageSize) {
        setMaxMessageSize(Long.valueOf(maxMessageSize));
    }

    public void setMaxMessageSize(Long maxMessageSize) {
        if (custody != null || alternativeAddress != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.01 specific signaling option already set");
        }
        if (maxMessageSize != null && (maxMessageSize < 0 || maxMessageSize > 0xFFFFFFFFL)) {
            throw new IllegalArgumentException("Illegal Max-Message-Size argument: " + maxMessageSize);
        }
        this.maxMessageSize = maxMessageSize;
    }

    public Boolean getBlockWiseTransfer() {
        return blockWiseTransfer;
    }

    public void setBlockWiseTransfer(Boolean blockWiseTransfer) {
        if (custody != null || alternativeAddress != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.01 specific signaling option already set");
        }
        this.blockWiseTransfer = blockWiseTransfer;
    }

    public Boolean getCustody() {
        return custody;
    }

    public void setCustody(Boolean custody) {
        if (maxMessageSize != null || blockWiseTransfer != null || alternativeAddress != null || holdOff != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.02 or 7.03 specific signaling option already set");
        }
        this.custody = custody;
    }

    public String getAlternativeAddress() {
        return alternativeAddress;
    }

    public void setAlternativeAddress(String alternativeAddress) {
        if (maxMessageSize != null || blockWiseTransfer != null || custody != null || badCsmOption != null) {
            throw new IllegalStateException("Other than 7.04 specific signaling option already set");
        }

        this.alternativeAddress = alternativeAddress;
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
        if (maxMessageSize != null || blockWiseTransfer != null || custody != null || alternativeAddress != null || holdOff != null) {
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

        SignalingOptions that = (SignalingOptions) o;

        if (maxMessageSize != null ? !maxMessageSize.equals(that.maxMessageSize) : that.maxMessageSize != null) {
            return false;
        }
        if (blockWiseTransfer != null ? !blockWiseTransfer.equals(that.blockWiseTransfer) : that.blockWiseTransfer != null) {
            return false;
        }
        if (custody != null ? !custody.equals(that.custody) : that.custody != null) {
            return false;
        }
        if (alternativeAddress != null ? !alternativeAddress.equals(that.alternativeAddress) : that.alternativeAddress != null) {
            return false;
        }
        if (holdOff != null ? !holdOff.equals(that.holdOff) : that.holdOff != null) {
            return false;
        }
        return badCsmOption != null ? badCsmOption.equals(that.badCsmOption) : that.badCsmOption == null;
    }

    @Override
    public int hashCode() {
        int result = maxMessageSize != null ? maxMessageSize.hashCode() : 0;
        result = 31 * result + (blockWiseTransfer != null ? blockWiseTransfer.hashCode() : 0);
        result = 31 * result + (custody != null ? custody.hashCode() : 0);
        result = 31 * result + (alternativeAddress != null ? alternativeAddress.hashCode() : 0);
        result = 31 * result + (holdOff != null ? holdOff.hashCode() : 0);
        result = 31 * result + (badCsmOption != null ? badCsmOption.hashCode() : 0);
        return result;
    }
}
