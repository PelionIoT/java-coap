package com.mbed.coap.server.internal;

import java.util.Optional;

/**
 * Created by olesmi01 on 26.07.2017.
 */
public class CoapTcpCSM {
    public static final CoapTcpCSM BASE = new CoapTcpCSM();

    // see https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.3 for base values (5.3.1, 5.3.2)
    private static final int BASE_MAX_MESSAGE_SIZE = 1152;
    private static final boolean BASE_BLOCKWISE = false;

    private final boolean blockwiseTransfer;
    private final long maxMessageSize;

    private CoapTcpCSM() {
        blockwiseTransfer = BASE_BLOCKWISE;
        maxMessageSize = BASE_MAX_MESSAGE_SIZE;
    }

    private CoapTcpCSM(long maxMessageSize, boolean blockwiseTransfer) {
        this.maxMessageSize = maxMessageSize;
        this.blockwiseTransfer = blockwiseTransfer;
    }

    public CoapTcpCSM withMaxMessageSize(long maxMessageSize) {
        return new CoapTcpCSM(maxMessageSize, this.blockwiseTransfer);
    }

    public CoapTcpCSM withBlockTransferBERT(boolean blockwiseTransfer) {
        return new CoapTcpCSM(this.maxMessageSize, blockwiseTransfer);
    }

    public CoapTcpCSM withNewOptions(Long maxMessageSize, Boolean blockwiseTransfer) {
        long newMaxSize = Optional.ofNullable(maxMessageSize).orElse(this.maxMessageSize);
        boolean newBlockWise = Optional.ofNullable(blockwiseTransfer).orElse(this.blockwiseTransfer);

        if (newMaxSize == BASE.maxMessageSize
                && newBlockWise == BASE.blockwiseTransfer) {
            return BASE;
        }

        return new CoapTcpCSM(newMaxSize, newBlockWise);
    }

    public boolean isBlockTransferEnabled() {
        return blockwiseTransfer;
    }

    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    public boolean isBERTEnabled() {
        return blockwiseTransfer && maxMessageSize > BASE_MAX_MESSAGE_SIZE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CoapTcpCSM that = (CoapTcpCSM) o;

        if (blockwiseTransfer != that.blockwiseTransfer) {
            return false;
        }
        return maxMessageSize == that.maxMessageSize;
    }

    @Override
    public int hashCode() {
        int result = (blockwiseTransfer ? 1 : 0);
        result = 31 * result + (int) (maxMessageSize ^ (maxMessageSize >>> 32));
        return result;
    }
}
