package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.annotation.JsonCreator
import com.google.common.collect.ImmutableMap
import com.google.common.io.BaseEncoding
import org.jivesoftware.smack.packet.IQ
import java.util.*

internal object MessageAuth {
    var MIME_TYPE = "vnd.logitech.connect/vnd.logitech.pair"

    /*
     * Request
     */
    class AuthRequest : OAStanza(MIME_TYPE) {
        init {
            type = Type.get
        }

        //
        protected override val childElementPairs: Map<String, Any?>
            protected get() = ImmutableMap.builder<String, Any?>() //
                .put("method", "pair")
                .put("name", generateUniqueId() + "#" + deviceIdentifier)
                .build()

        private fun generateUniqueId(): String {
            return BaseEncoding.base64().encode(UUID.randomUUID().toString().toByteArray())
        }

        private val deviceIdentifier: String
            private get() = "iOS6.0.1#iPhone"
    }

    /*
     * Reply
     */
    class AuthReply @JsonCreator constructor() : OAStanza(MIME_TYPE) {
        val serverIdentity: String? = null
        val hubId: String? = null
        val password: String? = null
        val status: String? = null
        val protocolVersion: Map<String, String>? = null
        val hubProfiles: Map<String, String>? = null
        val productId: String? = null
        val friendlyName: String? = null

        //
        protected override val childElementPairs: Map<String, Any?>
            protected get() = ImmutableMap.builder<String, Any?>() //
                .put("serverIdentity", serverIdentity)
                .put("hubId", hubId)
                .put("identity", password)
                .put("status", status)
                .put("protocolVersion", protocolVersion)
                .put("hubProfiles", hubProfiles)
                .put("productId", productId)
                .put("friendlyName", friendlyName)
                .build()
        val username: String
            get() = String.format("%s@connect.logitech.com/gatorade", password)
    }

    /*
     * Parser
     */
    class AuthReplyParser : OAReplyParser() {
        override fun parseReplyContents(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): IQ {
            return Jackson.OBJECT_MAPPER.convertValue<AuthReply>(
                OAReplyParser.Companion.parseKeyValuePairs(
                    statusCode,
                    errorString,
                    contents
                ), AuthReply::class.java
            )
        }
    }
}