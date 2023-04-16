package net.gmx.szermatt.dreamcatcher.harmony

import com.google.common.collect.ImmutableMap
import org.jivesoftware.smack.packet.IQ

internal object MessageStartActivity {
    const val MIME_TYPE = "vnd.logitech.harmony/vnd.logitech.harmony.engine?startactivity"
    const val MIME_TYPE2 = "harmony.engine?startActivity"

    /*
     * Request
     */
    class StartActivityRequest(private val activityId: Int) : IrCommand(MIME_TYPE) {
        //
        protected override val childElementPairs: Map<String, Any?>
            protected get() = ImmutableMap.builder<String, Any?>() //
                .put("activityId", activityId)
                .put("timestamp", generateTimestamp())
                .build()
    }

    /*
     * Reply (unused)
     */
    class StartActivityReply : OAStanza(MIME_TYPE) {
        //
        protected override val childElementPairs: Map<String, Any?>
            protected get() = ImmutableMap.builder<String, Any?>() //
                .build()
    }

    /*
     * Parser
     */
    class StartActivityReplyParser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): IQ {
            return Jackson.OBJECT_MAPPER.convertValue<StartActivityReply>(
                OAReplyParser.Companion.parseKeyValuePairs(statusCode, errorString, contents),
                StartActivityReply::class.java
            )
        }

        override fun validResponseCode(code: String?): Boolean {
            //sometimes the start activity will return a 401 if a device is not setup correctly
            return super.validResponseCode(code) || code == "401"
        }
    }
}