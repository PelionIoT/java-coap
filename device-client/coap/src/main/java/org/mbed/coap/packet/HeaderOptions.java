/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */
package org.mbed.coap.packet;

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
    private Integer observe;
    private BlockOption block1Req;
    private BlockOption block2Res;

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
    }

    /**
     * @return the subsLifetime
     */
    public Integer getObserve() {
        return observe;
    }

    /**
     * Sets observer option value. Allowed value range: 0-65535.
     *
     * @param observe the subsLifetime to set
     */
    public void setObserve(Integer observe) {
        if (observe < 0 || observe > 0xFFFF) {
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

    /**
     * @param block the block to set
     */
    public void setBlock1Req(BlockOption block) {
        this.block1Req = block;
    }

    public void setBlock2Res(BlockOption block) {
        this.block2Res = block;
    }

    @Override
    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity", "PMD.PrematureDeclaration"})
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HeaderOptions other = (HeaderOptions) obj;
        if (!super.equals(obj)) {
            return false;
        }
        if (this.observe != other.observe && (this.observe == null || !this.observe.equals(other.observe))) {
            return false;
        }
        if (this.block1Req != other.block1Req && (this.block1Req == null || !this.block1Req.equals(other.block1Req))) {
            return false;
        }
        if (this.block2Res != other.block2Res && (this.block2Res == null || !this.block2Res.equals(other.block2Res))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 29 * hash + (this.observe != null ? this.observe.hashCode() : 0);
        hash = 29 * hash + (this.block1Req != null ? this.block1Req.hashCode() : 0);
        hash = 29 * hash + (this.block2Res != null ? this.block2Res.hashCode() : 0);
        return hash;
    }
}
