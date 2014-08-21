package com.arm.mbed.commons.lwm2m.model;

import static com.arm.mbed.commons.lwm2m.model.Type.EXECUTABLE;
import static com.arm.mbed.commons.lwm2m.model.Type.STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.arm.mbed.commons.lwm2m.model.InvalidResourceURIException;
import com.arm.mbed.commons.lwm2m.model.ObjectRegistry;
import com.arm.mbed.commons.lwm2m.model.Type;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.junit.Before;
import org.junit.Test;

/**
 * @author nordav01
 */
public class ObjectRegistryTest {

    private static final String LWM2M_TEST_OBJECTS_JSON = "lwm2m-test-objects.json";
    private ObjectRegistry registry;

    @Before
    public void before() {
        registry = ObjectRegistry.createObjectRegistry();
    }

    @Test
    public void getTypeOfResourceOfMultipleObject() throws Exception {
        Type type = registry.getOmaResourceType("/0/0/0");
        assertEquals(STRING, type);
    }

    @Test
    public void getTypeOfResourceOfSingleObject() throws Exception {
        Type type = registry.getOmaResourceType("/3//4");
        assertEquals(EXECUTABLE, type);
    }

    @Test(expected = InvalidResourceURIException.class)
    public void getTypeOfResourceWithObjectURI() throws Exception {
        registry.getOmaResourceType("/3");
    }

    @Test
    public void getTypeOfUnknownResourceOfKnownObject() throws Exception {
        assertNull(registry.getOmaResourceType("/0/0/unknown"));
    }

    @Test
    public void getTypeOfResourceOfUnknownObject() throws Exception {
        assertNull(registry.getOmaResourceType("/.unknown//0"));
    }

    @Test(expected = InvalidResourceURIException.class)
    public void getTypeOfInvalidResourceURI() throws Exception {
        registry.getOmaResourceType("invalid");
    }

    @Test(expected = NullPointerException.class)
    public void getTypeOfNullResourceURI() throws Exception {
        registry.getOmaResourceType(null);
    }

    @Test
    public void addObjectModelsToObjectRegistry() throws Exception {
        int baseSize = registry.getObjectModels().size();
        InputStream stream = this.getClass().getResourceAsStream(LWM2M_TEST_OBJECTS_JSON);
        Reader reader = new InputStreamReader(stream);
        registry.addObjectModels(ObjectRegistry.createObjectRegistry(reader).getObjectModels());

        assertEquals(baseSize + 2, registry.getObjectModels().size());
        assertNotNull(registry.getObjectModel("test"));
        assertNotNull(registry.getObjectModel("try").getResourceModel("0"));

        stream = this.getClass().getResourceAsStream(LWM2M_TEST_OBJECTS_JSON);
        reader = new InputStreamReader(stream);
        registry.addObjectModels(ObjectRegistry.createObjectRegistry(reader).getObjectModels());
        assertEquals(baseSize + 2, registry.getObjectModels().size());
    }

}
