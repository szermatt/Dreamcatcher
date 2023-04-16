package net.gmx.szermatt.dreamcatcher.harmony

import net.gmx.szermatt.dreamcatcher.harmony.MessageAuth.AuthReplyParser
import net.gmx.szermatt.dreamcatcher.harmony.MessageStartActivity.StartActivityReplyParser
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.provider.IQProvider
import org.xmlpull.v1.XmlPullParser

internal class OAReplyProvider : IQProvider<IQ?>() {
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

    companion object {
        private val replyParsers: MutableMap<String?, OAReplyParser> = HashMap()

        init {
            replyParsers[MessageAuth.MIME_TYPE] = AuthReplyParser()
            replyParsers[MessageStartActivity.MIME_TYPE] =
                StartActivityReplyParser()
            replyParsers[MessageStartActivity.MIME_TYPE2] = StartActivityReplyParser()
        }
    }
}