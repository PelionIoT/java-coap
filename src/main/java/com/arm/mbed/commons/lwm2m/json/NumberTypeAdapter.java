/**
 * Copyright (C) 2011-2014 ARM Limited. All rights reserved.
 */

package com.arm.mbed.commons.lwm2m.json;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import com.google.gson.TypeAdapter;

/**
 * @author nordav01
 */
class NumberTypeAdapter extends TypeAdapter<Number> {

    @Override
    public void write(JsonWriter writer, Number value) throws IOException {
        if (value != null) {
            writer.value(value.toString() );
        } else {
            writer.nullValue();
        }
    }

    @Override
    public Number read(JsonReader reader) throws IOException {
        String string = reader.nextString();
        Number number;
        
        if (string.indexOf('.') == -1) {
            number = Integer.parseInt(string);
        } else {
            number = Double.parseDouble(string);
        }
        
        return number;
    }

}
