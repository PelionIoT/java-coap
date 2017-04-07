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

import java.util.List;

/**
 * Implements CoAP additional header options from draft-ietf-core-block-12 and
 * draft-ietf-core-observe-09.
 *
 * @author szymon
 */
public class HeaderOptions extends BasicHeaderOptions {

    private static final byte OBSERVE = 6;
    private static final byte BLOCK_1_REQ = 27;
    private static final byte BLOCK_2_RES = 23;
    private static final byte SIZE_2_RES = 28;
    private Integer observe;
    private BlockOption block1Req;
    private BlockOption block2Res;
    private Integer size2Res;

    @Override
    public boolean parseOption(int type, byte[] data) {
        if (super.parseOption(type, data)) {
            return true;
        }

        switch (type) {
            case OBSERVE:
                setObserve(DataConvertingUtility.readVariableULong(data).intValue());
                break;
            case BLOCK_2_RES:
                setBlock2Res(new BlockOption(data));
                break;
            case BLOCK_1_REQ:
                setBlock1Req(new BlockOption(data));
                break;
            case SIZE_2_RES:
                setSize2Res(DataConvertingUtility.readVariableULong(data).intValue());
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    protected List<RawOption> getRawOptions() {
        List<RawOption> l = super.getRawOptions();
        if (observe != null) {
            if (observe == 0) {
                l.add(RawOption.fromEmpty(OBSERVE));
            } else {
                l.add(RawOption.fromUint(OBSERVE, observe.longValue()));
            }
        }
        if (block1Req != null) {
            l.add(new RawOption(BLOCK_1_REQ, new byte[][]{getBlock1Req().toBytes()}));
        }
        if (block2Res != null) {
            l.add(new RawOption(BLOCK_2_RES, new byte[][]{getBlock2Res().toBytes()}));
        }
        if (size2Res != null) {
            l.add(RawOption.fromUint(SIZE_2_RES, size2Res.longValue()));
        }

        return l;
    }

    @Override
    public void toString(StringBuilder sb) {
        super.toString(sb);

        if (block1Req != null) {
            sb.append(" block1:").append(block1Req);
        }
        if (block2Res != null) {
            sb.append(" block2:").append(block2Res);
        }
        if (observe != null) {
            sb.append(" obs:").append(observe);
        }
        if (size2Res != null) {
            sb.append(" sz2:").append(size2Res);
        }
    }

    /**
     * @return the subsLifetime
     */
    public Integer getObserve() {
        return observe;
    }

    /**
     * Sets observer option value. Allowed value range: 3 bytes.
     *
     * @param observe the subsLifetime to set
     */
    public void setObserve(Integer observe) {
        if (observe < 0 || observe > 0xFFFFFF) {
            throw new IllegalArgumentException("Illegal observer argument: " + observe);
        }
        this.observe = observe;
    }

    /**
     * @return the request block
     */
    public BlockOption getBlock1Req() {
        return block1Req;
    }

    public BlockOption getBlock2Res() {
        return block2Res;
    }

    public Integer getSize2Res() {
        return size2Res;
    }

    /**
     * @param block the block to set
     */
    public void setBlock1Req(BlockOption block) {
        this.block1Req = block;
    }

    public void setBlock2Res(BlockOption block) {
        this.block2Res = block;
    }

    public void setSize2Res(Integer size2Res) {
        this.size2Res = size2Res;
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

        HeaderOptions that = (HeaderOptions) o;

        if (observe != null ? !observe.equals(that.observe) : that.observe != null) {
            return false;
        }
        if (block1Req != null ? !block1Req.equals(that.block1Req) : that.block1Req != null) {
            return false;
        }
        if (block2Res != null ? !block2Res.equals(that.block2Res) : that.block2Res != null) {
            return false;
        }
        return size2Res != null ? size2Res.equals(that.size2Res) : that.size2Res == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (observe != null ? observe.hashCode() : 0);
        result = 31 * result + (block1Req != null ? block1Req.hashCode() : 0);
        result = 31 * result + (block2Res != null ? block2Res.hashCode() : 0);
        result = 31 * result + (size2Res != null ? size2Res.hashCode() : 0);
        return result;
    }
}
