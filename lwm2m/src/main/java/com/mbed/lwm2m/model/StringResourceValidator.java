/*
 * Copyright (C) 2011-2015 ARM Limited. All rights reserved.
 */
package com.mbed.lwm2m.model;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author nordav01
 */
 class StringResourceValidator {

     private static class StringValidator implements ResourceValidator {

        @Override
        public boolean isValid(byte[] value) {
            return value != null && isValid(new String(value, Charset.defaultCharset() ) );
        }

        @Override
        public boolean isValid(int value) {
            return false;
        }

        @Override
        public boolean isValid(String value) {
            return value != null;
        }

     }
     
     private static class StringLengthValidator extends StringValidator {

        private int lengthMin;
        private int lengthMax;
         
        public StringLengthValidator (String range) {
            String[] length = range.split(",");
            if ("".equals(range) ) {
                lengthMin = 0;
                lengthMax = Integer.MAX_VALUE;
            } else if (length.length == 1) {
                lengthMin = lengthMax = Integer.parseInt(length[0]);
            } else if (length.length > 1) {
                lengthMin = Integer.parseInt(length[0]);
                lengthMax = Integer.parseInt(length[1]);
            }
        } 

        @Override
        public boolean isValid(String value) {
            return super.isValid(value) && value.length()>=lengthMin && value.length()<=lengthMax;
        }
        
     }
     
     private static class StringOptionsValidator extends StringValidator {
         
         private final Set<String> options = new HashSet<>();
         
         public StringOptionsValidator (String range) {
             options.addAll(Arrays.asList(range.split(",")) );
         }

         @Override
         public boolean isValid(String value) {
             return super.isValid(value) && options.contains(value);
         }
     }
     
     static ResourceValidator createResourceValidator (String range) {
         if (!range.isEmpty() && range.charAt(0) == '{' && range.endsWith("}")) {
             return new StringLengthValidator(range.substring(1, range.length()-1));
         } else if (!range.isEmpty() && range.charAt(0) == '[' && range.endsWith("]")) {
             return new StringOptionsValidator(range.substring(1, range.length()-1));
         }
         
         return new StringValidator();
     }
     
}