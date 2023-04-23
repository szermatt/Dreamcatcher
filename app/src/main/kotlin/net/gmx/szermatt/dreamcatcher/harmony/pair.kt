package net.gmx.szermatt.dreamcatcher.harmony

import com.google.common.io.BaseEncoding
import org.jivesoftware.smack.packet.IQ
import java.util.*

/** Request for obtaining the session token. */
internal class PairRequest : OAStanza(HarmonyMimeTypes.PAIR) {
    init {
        type = Type.get
    }

    override val childElementPairs: Map<String, Any?>
        get() = mapOf(
            "method" to "pair",
            "name" to generateUniqueId() + "#" + deviceIdentifier,
        )

    private fun generateUniqueId(): String {
        return BaseEncoding.base64().encode(UUID.randomUUID().toString().toByteArray())
    }

    private val deviceIdentifier: String
        get() = "iOS6.0.1#iPhone"
}

/** Reply containing the session token. */
internal class PairReply(val identity: String? = null) : OAStanza(HarmonyMimeTypes.PAIR) {
    override val childElementPairs: Map<String, Any?>
        get() = if (identity == null) {
            mapOf()
        } else {
            mapOf("identity" to identity)
        }


    /** Parser for these replies. */
    internal class Parser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): IQ {
            val contentMap = parseContentMap(contents)
            return PairReply(identity = contentMap["identity"])
        }
    }
}