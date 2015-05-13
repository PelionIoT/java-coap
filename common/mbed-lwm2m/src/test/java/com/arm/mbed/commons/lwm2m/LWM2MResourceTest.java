package com.arm.mbed.commons.lwm2m;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import com.arm.mbed.commons.string.Utf8Bytes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LWM2MResourceTest {

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void createWithNullID() {
        new LWM2MResource(null, 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithNegativeID() {
        new LWM2MResource(LWM2MID.from(-1), 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithLongID() {
        new LWM2MResource(LWM2MID.from(65536), 42);
    }

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void createWithNullNestedResourceInstances() {
        new LWM2MResource(LWM2MID.from(1), (List<LWM2MResourceInstance>) null);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void createWithEmptyNestedResourceInstances() {
        List<LWM2MResourceInstance> instances = Collections.emptyList();
        new LWM2MResource(LWM2MID.from(1), instances);
    }

    @Test
    public void testHasNested() throws Exception {
        List<LWM2MResourceInstance> instances = Arrays.asList(new LWM2MResourceInstance(LWM2MID.from(1), "instance"));
        LWM2MResource resource = new LWM2MResource(LWM2MID.from(1), instances);

        assertEquals(1, resource.getId().intValue());
        assertTrue(resource.hasNestedInstances());
        assertFalse(resource.hasValue());
        assertThat(resource.getNestedInstances(), hasSize(1));
        assertArrayEquals(Utf8Bytes.of("instance"), resource.getNestedInstances().get(0).getValue());
    }

}
