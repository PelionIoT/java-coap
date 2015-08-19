/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package com.arm.mbed.commons.lwm2m.model;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nordav01
 */
class IntegerResourceValidator {

    private static class IntegerValidator implements ResourceValidator {

        @Override
        public boolean isValid(byte[] value) {
            if (value != null && value.length > 0 && value.length < 5) {
                int intValue = 0;
                for (int i=0; i<value.length; i++) {
                    intValue = (intValue << 8) + (value[i] & 0xFF);
                }
                return isValid(intValue);
            }
            return false;
        }

        @Override
        public boolean isValid(int value) {
            return true;
        }

        @Override
        public boolean isValid(String value) {
            try {
                return isValid(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                return false;
            }
        }

    }
    
    private static class IntegerRangeValidator extends IntegerValidator {
        
       private int rangeMin;
       private int rangeMax;
        
       public IntegerRangeValidator (String range) {
           int index = range.indexOf('-');
           try {
               rangeMin = Integer.parseInt(range.substring(0,index) );
           } catch (NumberFormatException exception) {
               rangeMin = Integer.MIN_VALUE;
           }
           try {
               rangeMax = Integer.parseInt(range.substring(index+1) );
           } catch (NumberFormatException exception) {
               rangeMax = Integer.MAX_VALUE;
           }
       } 

       @Override
       public boolean isValid(int value) {
           return super.isValid(value) && value>=rangeMin && value<=rangeMax;
       }

    }
    
    private static class IntegerOptionsValidator extends IntegerValidator {
        
        private final Set<Integer> options = new HashSet<>();
        
        public IntegerOptionsValidator (String range) {
            for (String option: range.split(",")) {
                try {
                    options.add(Integer.parseInt(option) );
                } catch (NumberFormatException exception) {
                    // Skip non-integer values
                }
            }
        }

        @Override
        public boolean isValid(int value) {
            return super.isValid(value) && options.contains(value);
        }
        
    }

    static ResourceValidator createResourceValidator(String range) {
        if (!range.isEmpty() && range.charAt(0) == '[' && range.endsWith("]")) {
            if (range.indexOf('-') > -1) {
                return new IntegerRangeValidator(range.substring(1, range.length()-1));
            } else {
                return new IntegerOptionsValidator(range.substring(1, range.length()-1));
            }
        }
        
        return new IntegerValidator();
    }
    
}
