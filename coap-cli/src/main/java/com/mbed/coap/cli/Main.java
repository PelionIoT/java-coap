/*
 * Copyright (C) 2022-2023 java-coap contributors (https://github.com/open-coap/java-coap)
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
package com.mbed.coap.cli;

import com.mbed.coap.packet.BlockSize;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "coap", subcommands = {SendCommand.class, DeviceEmulator.class})
public class Main {

    public static void main(String[] args) {
        int exitCode = createCommandLine().execute(args);
        if (exitCode >= 0) {
            System.exit(exitCode);
        }
    }

    static CommandLine createCommandLine() {
        return new CommandLine(new Main())
                .registerConverter(BlockSize.class, s -> BlockSize.valueOf("S_" + s));
    }

}
