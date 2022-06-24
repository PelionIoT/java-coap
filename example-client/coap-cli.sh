#!/usr/bin/env bash

../gradlew :example-client:installDist -q
./build/install/coap-cli/bin/coap $@
