package com.arm.mbed.commons.lwm2m.transport;

import static org.junit.Assert.*;
import org.junit.Test;

public class TransportBindingTest {

    @Test
    public void parse() throws TransportBindingParseException {
        assertEquals(new TransportBinding(true, false, false), TransportBinding.parse("U"));
        assertEquals(new TransportBinding(false, true, false), TransportBinding.parse("S"));
        assertEquals(new TransportBinding(true, false, true), TransportBinding.parse("UQ"));
        assertEquals(new TransportBinding(true, true, true), TransportBinding.parse("UQS"));
    }

    @Test
    public void parse_faile() {
        assertParseFails("u");
        assertParseFails("UQSQ");
        assertParseFails("USQ");
        assertParseFails("s");
        assertParseFails("Us");
        assertParseFails("");
        assertParseFails("UqS");
    }

    private static void assertParseFails(String transBinding) {
        try {
            TransportBinding.parse(transBinding);
            fail();
        } catch (TransportBindingParseException ex) {
            //expected
        }
    }
}
