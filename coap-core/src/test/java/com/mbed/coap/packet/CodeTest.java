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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Created by szymon
 */
public class CodeTest {

    @Test
    public void test() throws Exception {
        assertEquals(412, Code.C412_PRECONDITION_FAILED.getHttpCode());


        assertEquals("504", Code.C504_GATEWAY_TIMEOUT.codeToString());
        //run through all
        for (Code code : Code.values()) {
            code.codeToString();
        }


        assertEquals(Code.C505_PROXYING_NOT_SUPPORTED, Code.valueOf(5, 5));
        assertEquals(null, Code.valueOf(6, 0));
    }
}