package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper

/** Holds a pre-configured Jackson object mapper for this package.  */
internal object Jackson {
    val OBJECT_MAPPER = ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
}