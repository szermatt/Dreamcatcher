package net.gmx.szermatt.dreamcatcher.harmony

import com.google.common.collect.ImmutableMap
import org.jivesoftware.smack.packet.IQ

/** Start Activity Request */
class StartActivityRequest(private val activityId: Int) :
    OAStanza(HarmonyMimeTypes.START_ACTIVITY) {
    override val childElementPairs: Map<String, Any?>
        get() = ImmutableMap.builder<String, Any?>()
            .put("activityId", activityId)
            .put("timestamp", generateTimestamp())
            .build()
}

/** Start Activity Reply (unused) */
class StartActivityReply : OAStanza(HarmonyMimeTypes.START_ACTIVITY) {
    override val childElementPairs: Map<String, Any?>
        get() = ImmutableMap.builder<String, Any?>().build()

    /** Parser for that kind of replies. */
    internal class Parser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): IQ {
            return Jackson.OBJECT_MAPPER.convertValue<StartActivityReply>(
                parseKeyValuePairs(statusCode, errorString, contents),
                StartActivityReply::class.java
            )
        }

        override fun validResponseCode(code: String?): Boolean {
            //sometimes the start activity will return a 401 if a device is not setup correctly
            return super.validResponseCode(code) || code == "401"
        }
    }
}