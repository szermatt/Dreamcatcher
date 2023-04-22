package net.gmx.szermatt.dreamcatcher.harmony

import com.google.common.collect.ImmutableMap
import com.google.common.io.BaseEncoding
import org.jivesoftware.smack.packet.IQ
import java.util.*

/** Request for obtaining the session token. */
class PairRequest : OAStanza(HarmonyMimeTypes.PAIR) {
    init {
        type = Type.get
    }

    override val childElementPairs: Map<String, Any?>
        get() = ImmutableMap.builder<String, String?>() //
            .put("method", "pair")
            .put("name", generateUniqueId() + "#" + deviceIdentifier)
            .build()

    private fun generateUniqueId(): String {
        return BaseEncoding.base64().encode(UUID.randomUUID().toString().toByteArray())
    }

    private val deviceIdentifier: String
        get() = "iOS6.0.1#iPhone"
}

/** Reply containing the session token. */
class PairReply(val identity: String? = null) : OAStanza(HarmonyMimeTypes.PAIR) {
    override val childElementPairs: Map<String, Any?>
        get() {
            if (identity == null) {
                return mapOf()
            } else {
                return mapOf("identity" to identity)
            }
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