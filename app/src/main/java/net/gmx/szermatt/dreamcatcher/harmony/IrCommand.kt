package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.collect.ImmutableMap

internal abstract class IrCommand(mimeType: String?) : OAStanza(mimeType) {
    fun generateAction(deviceId: Int, button: String): String {
        return try {
            Jackson.OBJECT_MAPPER.writeValueAsString(
                ImmutableMap.builder<String, Any>() //
                    .put("type", "IRCommand")
                    .put("deviceId", Integer.valueOf(deviceId).toString())
                    .put("command", button)
                    .build()
            ).replace(":".toRegex(), "::")
        } catch (e: JsonProcessingException) {
            throw RuntimeException(e)
        }
    }
}