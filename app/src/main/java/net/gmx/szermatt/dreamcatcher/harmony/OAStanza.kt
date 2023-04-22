package net.gmx.szermatt.dreamcatcher.harmony

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.base.Joiner
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.filter.*
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.SimpleIQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.provider.IQProvider
import org.jxmpp.jid.Jid
import org.xmlpull.v1.XmlPullParser
import java.util.regex.Pattern

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
abstract class OAStanza(protected val mimeType: String?) :
    IQ(object : SimpleIQ("oa", "connect.logitech.com") {}) {
    var statusCode: String? = null
    var errorString: String? = null

    @JsonIgnore // Subclasses use a Jackson object mapper that throws an exception for properties with multiple setters
    override fun setError(error: XMPPError) {
        super.setError(error)
    }

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
        return validResponses.contains(code)
    }

    companion object {
        private val kvRE: Pattern = Pattern.compile("(.*?)=(.*)")
        var validResponses: MutableSet<String?> = HashSet()

        init {
            validResponses.add("100")
            validResponses.add("200")
            validResponses.add("506") // Bluetooth not connected
            validResponses.add("566") // Command not found for device, recoverable
        }

        @JvmStatic
        protected fun parseKeyValuePairs(
            statusCode: String?,
            errorString: String?,
            contents: String
        ): Map<String, Any> {
            val params: MutableMap<String, Any> = HashMap()
            if (statusCode != null) {
                params["statusCode"] = statusCode
            }
            if (errorString != null) {
                params["errorString"] = errorString
            }
            for (pair in contents.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                val matcher = kvRE.matcher(pair)
                if (!matcher.matches()) {
                    continue
                    // throw new AuthFailedException(format("failed to parse element in auth response: %s", pair));
                }
                var valueObj: Any
                val value = matcher.group(2)
                valueObj = if (value?.startsWith("{") == true) {
                    parsePseudoJson(value)
                } else {
                    value
                }
                params[matcher.group(1)!!] = valueObj
            }
            return params
        }

        @JvmStatic
        protected fun parsePseudoJson(v: String): Map<String, Any> {
            val params: MutableMap<String, Any> = HashMap()
            val value = v.substring(1, v.length - 1)
            for (pair in value.split(", ?".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                val matcher = kvRE.matcher(pair)
                if (!matcher.matches()) {
                    throw AuthFailedException(
                        String.format(
                            "failed to parse element in auth response: %s",
                            value
                        )
                    )
                }
                params[matcher.group(1)!!] = parsePseudoJsonValue(matcher.group(2)!!)
            }
            return params
        }

        private fun parsePseudoJsonValue(value: String): Any {
            return when (value[0]) {
                '{' -> parsePseudoJsonValue(value)
                '"' -> value.substring(1, value.length - 1)
                '\'' -> value.substring(1, value.length - 1)
                else -> {
                    try {
                        return value.toLong()
                    } catch (e: NumberFormatException) {
                        // do nothing
                    }
                    try {
                        return value.toDouble()
                    } catch (e: NumberFormatException) {
                        // do nothing
                    }
                    value
                }
            }
        }
    }
}

/** Parses replies from the OA stanza, based on their mime type. */
internal class OAReplyProvider : IQProvider<IQ?>() {
    companion object {
        private val replyParsers: MutableMap<String?, OAReplyParser> = HashMap()

        init {
            replyParsers[HarmonyMimeTypes.PAIR] = PairReply.Parser()
            replyParsers[HarmonyMimeTypes.START_ACTIVITY] =
                StartActivityReply.Parser()
            replyParsers[HarmonyMimeTypes.START_ACTIVITY_SHORT] =
                StartActivityReply.Parser()
        }
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
        val replyParser = replyParsers[mimeType]
            ?: throw HarmonyProtocolException(
                String.format(
                    "Unable to handle reply type '%s'",
                    mimeType
                )
            )
        if (!replyParser.validResponseCode(statusCode)) {
            throw HarmonyProtocolException(
                String.format(
                    "Got error response [%s]: %s",
                    statusCode,
                    attrs["errorstring"]
                )
            )
        }
        val contents = StringBuilder()
        var done = false
        while (!done) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> {
                    if (parser.name == elementName) {
                        done = true
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
        // First filter out everything that is not an IQ stanza and does not have the correct ID set.
        if (!iqAndIdFilter.accept(stanza)) {
            return false
        }

        // Second, check if the from attributes are correct and log potential IQ spoofing attempts
        return if (fromFilter.accept(stanza)) {
            true
        } else {
            println(
                String.format(
                    "Rejected potentially spoofed reply to IQ-stanza. Filter settings: "
                            + "stanzaId=%s, to=%s, local=%s, server=%s. Received stanza with from=%s",
                    stanzaId, to, local, server, stanza.from
                )
            )
            false
        }
    }
}
