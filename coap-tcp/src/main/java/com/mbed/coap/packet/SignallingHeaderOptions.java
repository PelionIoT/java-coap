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

import static com.mbed.coap.utils.Validations.require;
import java.util.List;
import java.util.Objects;

/**
 * Implements CoAP additional header options from
 * - RFC 8323
 */
public class SignallingHeaderOptions extends HeaderOptions {

    private static final byte SIGN_OPTION_2 = 2;
    private static final byte SIGN_OPTION_4 = 4;
    private final Code code;
    private Opaque signallingOption2;
    private Opaque signallingOption4;

    public SignallingHeaderOptions(Code code) {
        require(code.isSignaling());
        this.code = code;
    }

    @Override
    public boolean parseOption(int type, Opaque data) {
        switch (type) {
            case SIGN_OPTION_2:
                signallingOption2 = data;
                break;
            case SIGN_OPTION_4:
                signallingOption4 = data;
                break;
            default:
                return super.parseOption(type, data);

        }
        return true;
    }

    @Override
    protected List<RawOption> getRawOptions() {
        List<RawOption> l = super.getRawOptions();
        if (signallingOption2 != null) {
            l.add(new RawOption(SIGN_OPTION_2, signallingOption2));
        }
        if (signallingOption4 != null) {
            l.add(new RawOption(SIGN_OPTION_4, signallingOption4));
        }

        return l;
    }

    @Override
    public void buildToString(StringBuilder sb) {
        super.buildToString(sb);

        if (signallingOption2 != null || signallingOption4 != null) {
            SignalingOptions signOpt = new SignalingOptions();
            if (signallingOption2 != null) {
                signOpt.parse(2, signallingOption2, code);
            }
            if (signallingOption4 != null) {
                signOpt.parse(4, signallingOption4, code);
            }
            sb.append(signOpt.toString());
        }
    }

    public SignalingOptions toSignallingOptions(Code code) {
        if (signallingOption2 == null && signallingOption4 == null) {
            return null;
        } else {
            SignalingOptions signalingOptions = new SignalingOptions();
            if (signallingOption2 != null) {
                signalingOptions.parse(SIGN_OPTION_2, signallingOption2, code);
            }
            if (signallingOption4 != null) {
                signalingOptions.parse(SIGN_OPTION_4, signallingOption4, code);
            }
            return signalingOptions;
        }
    }

    public void putSignallingOptions(SignalingOptions signalingOptions) {
        this.signallingOption2 = signalingOptions.serializeOption2();
        this.signallingOption4 = signalingOptions.serializeOption4();
    }

    @Override
    public HeaderOptions duplicate() {
        SignallingHeaderOptions opts = new SignallingHeaderOptions(code);
        super.duplicate(opts);

        opts.signallingOption2 = signallingOption2;
        opts.signallingOption4 = signallingOption4;

        return opts;
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

        SignallingHeaderOptions that = (SignallingHeaderOptions) o;
        return code == that.code && Objects.equals(signallingOption2, that.signallingOption2) && Objects.equals(signallingOption4, that.signallingOption4);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, signallingOption2, signallingOption4);
    }
}
