package net.gmx.szermatt.dreamcatcher.harmony

import com.google.common.base.Joiner
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.SimpleIQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.provider.IQProvider
import org.jxmpp.jid.Jid
import org.xmlpull.v1.XmlPullParser

/** Mime types of the different OA stanzas supported by [OAReplyProvider]. */
internal class HarmonyMimeTypes {
    companion object {
        /** Mime type for obtaining a session token, see [PairRequest]. */
        val PAIR = "vnd.logitech.connect/vnd.logitech.pair"

        /** Mime type for starting an activity, see [StartActivityRequest]. */
        val START_ACTIVITY = "vnd.logitech.harmony/vnd.logitech.harmony.engine?startactivity"

        /** Short version of a mime type for starting an activity, see [StartActivityRequest]. */
        val START_ACTIVITY_SHORT = "harmony.engine?startActivity"
    }
}

/** The XMPP stanza used to send harmony-specific commands. */
abstract internal class OAStanza(protected val mimeType: String?) :
    IQ(object : SimpleIQ("oa", "connect.logitech.com") {}) {
    var statusCode: String? = null
    var errorString: String? = null

    override fun getIQChildElementBuilder(xml: IQChildElementXmlStringBuilder): IQChildElementXmlStringBuilder {
        if (statusCode != null) {
            xml.attribute("errorcode", statusCode)
        }
        if (errorString != null) {
            xml.attribute("errorstring", errorString)
        }
        xml.attribute("mime", mimeType)
        xml.rightAngleBracket()
        xml.append(joinChildElementPairs(childElementPairs))
        return xml
    }

    private fun joinChildElementPairs(pairs: Map<String, Any?>): String {
        val parts: MutableList<String> = ArrayList()
        for ((key, value) in pairs) {
            parts.add("$key=$value")
        }
        return Joiner.on(":").join(parts)
    }

    protected abstract val childElementPairs: Map<String, Any?>
    val isContinuePacket: Boolean
        get() = "100" == statusCode

    protected fun generateTimestamp(): Long {
        return System.currentTimeMillis() - CREATION_TIME
    }

    companion object {
        private val CREATION_TIME = System.currentTimeMillis()
    }
}

/** Parses a reply from the OA stanza. */
internal abstract class OAReplyParser {
    abstract fun parseReplyContents(statusCode: String?, errorString: String?, contents: String): IQ
    open fun validResponseCode(code: String?): Boolean {
        return VALID_RESPONSES.contains(code)
    }

    /**
     * Parses [contents] formatted as a series of key/values as a Map.
     *
     * Supported format: `key1=value1:key2=value2:...
     */
    protected fun parseContentMap(contents: String): Map<String, String> {
        return contents.split(':')
            .map {
                Pair(
                    it.substringBefore('='),
                    it.substringAfter('=')
                )
            }
            .toMap()
    }

    companion object {
        private val VALID_RESPONSES = setOf(
            "100",
            "200",
            "506", // Bluetooth not connected
            "566", // Command not found for device, recoverable
        )
    }
}

/** Parses replies from the OA stanza, based on their mime type. */
internal class OAReplyProvider : IQProvider<IQ?>() {
    companion object {
        private val REPLY_PARSERS: Map<String, OAReplyParser> = mapOf(
            HarmonyMimeTypes.PAIR to PairReply.Parser(),
            HarmonyMimeTypes.START_ACTIVITY to StartActivityReply.Parser(),
            HarmonyMimeTypes.START_ACTIVITY_SHORT to StartActivityReply.Parser(),
        )
    }

    @Throws(Exception::class)
    override fun parse(parser: XmlPullParser, initialDepth: Int): IQ? {
        val elementName = parser.name
        val attrs: MutableMap<String, String> = HashMap()
        for (i in 0 until parser.attributeCount) {
            val prefix = parser.getAttributePrefix(i)
            if (prefix != null) {
                attrs[prefix + ":" + parser.getAttributeName(i)] = parser.getAttributeValue(i)
            } else {
                attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
            }
        }
        val statusCode = attrs["errorcode"]
        val errorString = attrs["errorstring"]
        val mimeType = parser.getAttributeValue(null, "mime")
        val replyParser = REPLY_PARSERS[mimeType]
            ?: throw HarmonyProtocolException(
                "Unable to handle reply type '$mimeType'"
            )
        if (!replyParser.validResponseCode(statusCode)) {
            throw HarmonyProtocolException(
                "Got error response [$statusCode]: ${attrs["errorstring"]}"
            )
        }
        val contents = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> {
                    if (parser.name == elementName) {
                        break
                    }
                    contents.append(parser.text)
                }
                else -> contents.append(parser.text)
            }
        }
        return replyParser.parseReplyContents(statusCode, errorString, contents.toString())
    }
}

/** Filters out OA stanzas. */
internal class OAReplyFilter(request: OAStanza, connection: XMPPConnection) : StanzaFilter {
    // Copied from IQReplyFilter, but tweaked to support the Harmony's response pattern

    private val iqAndIdFilter: StanzaFilter
    private val fromFilter: OrFilter
    private val to: Jid?
    private var local: Jid? = null
    private val server: Jid
    private val stanzaId: String

    init {
        to = request.to
        local = if (connection.user == null) {
            // We have not yet been assigned a username, this can happen if the connection is
            // in an early stage, i.e. when performing the SASL auth.
            null
        } else {
            connection.user
        }
        server = connection.serviceName
        stanzaId = request.stanzaId
        val iqFilter: StanzaFilter = OrFilter(IQTypeFilter.ERROR, IQTypeFilter.GET)
        val idFilter: StanzaFilter = StanzaIdFilter(request.stanzaId)
        iqAndIdFilter = AndFilter(iqFilter, idFilter)
        fromFilter = OrFilter()
        fromFilter.addFilter(FromMatchesFilter.createFull(to))
        val l = local
        if (to == null) {
            if (l != null) {
                fromFilter.addFilter(FromMatchesFilter.createBare(l))
            }
            fromFilter.addFilter(FromMatchesFilter.createFull(server))
        } else if (l != null && to.equals(l.asBareJid())) {
            fromFilter.addFilter(FromMatchesFilter.createFull(null))
        }
    }

    override fun accept(stanza: Stanza): Boolean {
        return iqAndIdFilter.accept(stanza) && fromFilter.accept(stanza)
    }
}
