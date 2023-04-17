package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.annotation.JsonCreator
import com.google.common.collect.ImmutableMap
import com.google.common.io.BaseEncoding
import org.jivesoftware.smack.packet.IQ
import java.util.*

internal object MessageAuth {
    var MIME_TYPE = "vnd.logitech.connect/vnd.logitech.pair"

    /** Request */
    class AuthRequest : OAStanza(MIME_TYPE) {
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

    /** Reply */
    class AuthReply @JsonCreator constructor() : OAStanza(MIME_TYPE) {
        val serverIdentity: String? = null
        val hubId: String? = null
        val password: String? = null
        val status: String? = null
        val protocolVersion: Map<String, String>? = null
        val hubProfiles: Map<String, String>? = null
        val productId: String? = null
        val friendlyName: String? = null

        override val childElementPairs: Map<String, Any?>
            get() {
                val b = ImmutableMap.builder<String, Any?>()
                if (serverIdentity != null) b.put("serverIdentity", serverIdentity)
                if (hubId != null) b.put("hubId", hubId)
                if (password != null) b.put("identity", password)
                if (status != null) b.put("status", status)
                if (protocolVersion != null) b.put("protocolVersion", protocolVersion)
                if (hubProfiles != null) b.put("hubProfiles", hubProfiles)
                if (productId != null) b.put("productId", productId)
                if (friendlyName != null) b.put("friendlyName", friendlyName)
                return b.build()
            }
        val username: String
            get() = String.format("%s@connect.logitech.com/gatorade", password)
    }

    /** Parser */
    class AuthReplyParser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): IQ {
            return Jackson.OBJECT_MAPPER.convertValue(
                parseKeyValuePairs(
                    statusCode,
                    errorString,
                    contents
                ), MessageAuth.AuthReply::class.java
            )
        }
    }
}