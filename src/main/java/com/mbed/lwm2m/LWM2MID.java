/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.mbed.lwm2m;

import java.util.Objects;

/**
 * @author nordav01
 */
@SuppressWarnings("PMD.AvoidDollarSigns")
public class LWM2MID implements Comparable<LWM2MID> {

    public static final LWM2MID $0 = LWM2MID.from(0);
    public static final LWM2MID $1 = LWM2MID.from(1);
    public static final LWM2MID $2 = LWM2MID.from(2);
    public static final LWM2MID $3 = LWM2MID.from(3);
    public static final LWM2MID $4 = LWM2MID.from(4);
    public static final LWM2MID $5 = LWM2MID.from(5);
    public static final LWM2MID $6 = LWM2MID.from(6);
    public static final LWM2MID $7 = LWM2MID.from(7);
    public static final LWM2MID $8 = LWM2MID.from(8);
    public static final LWM2MID $9 = LWM2MID.from(9);
    public static final LWM2MID $10 = LWM2MID.from(10);
    public static final LWM2MID $11 = LWM2MID.from(11);
    public static final LWM2MID $12 = LWM2MID.from(12);
    public static final LWM2MID $13 = LWM2MID.from(13);
    public static final LWM2MID $14 = LWM2MID.from(14);
    public static final LWM2MID $15 = LWM2MID.from(15);
    public static final LWM2MID $16 = LWM2MID.from(16);
    
    private final String stringId;
    private int intId;
    
    public static LWM2MID create() {
        return new LWM2MID(null);
    }
    
    public static LWM2MID from (String stringId) {
        return new LWM2MID(stringId);
    }
    
    public static LWM2MID from (int intId) {
        return new LWM2MID(intId);
    }
    
    public LWM2MID (String stringId) {
        this.stringId = stringId;
        
        try {
            this.intId = Integer.parseInt(stringId);
        } catch (NumberFormatException exception) {
            this.intId = stringId != null ? stringId.hashCode() & 0x08FF : -1;
        }
    }
    
    public LWM2MID (int intId) {
        this.stringId = String.valueOf(intId);
        this.intId = intId;
    }
    
    public String stringValue() {
        return stringId;
    }
    
    public int intValue() {
        return intId;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LWM2MID) {
            LWM2MID that = (LWM2MID) obj;
            return Objects.equals(this.stringId,that.stringId);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(intId);
    }
    
    @Override
    public String toString() {
        return stringId;
    }

    @Override
    public int compareTo(LWM2MID that) {
        if (this.stringId == null) {
            return that.stringId == null ? 0 : -1; 
        } else {
            return that.stringId == null ? 1 : this.stringId.compareTo(that.stringId);
        }
    }

}
