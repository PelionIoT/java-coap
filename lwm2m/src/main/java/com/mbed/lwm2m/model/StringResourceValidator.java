/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2018 ARM Limited. All rights reserved.
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
package com.mbed.lwm2m.model;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

        private final int lengthMin;
        private final int lengthMax;
         
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
            } else {
                lengthMin = 0;
                lengthMax = 0;
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
