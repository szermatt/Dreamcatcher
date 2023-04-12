package net.gmx.szermatt.dreamcatcher.harmony;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.READ_ENUMS_USING_TO_STRING;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Jackson {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper() //
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(READ_ENUMS_USING_TO_STRING);
}
