package net.gmx.szermatt.dreamcatcher.harmony

/** Start Activity Request */
internal class StartActivityRequest(private val activityId: Int) :
    OAStanza(HarmonyMimeTypes.START_ACTIVITY) {
    override val childElementPairs: Map<String, Any?>
        get() = mapOf(
            "activityId" to activityId,
            "timestamp" to generateTimestamp()
        )
}

/** Start Activity Reply (empty). */
internal class StartActivityReply : OAStanza(HarmonyMimeTypes.START_ACTIVITY) {
    override val childElementPairs: Map<String, Any?>
        get() = mapOf()

    /** Parser for that kind of replies. */
    internal class Parser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ) = StartActivityReply()

        override fun validResponseCode(code: String?): Boolean {
            //sometimes the start activity will return a 401 if a device is not setup correctly
            return super.validResponseCode(code) || code == "401"
        }
    }
}