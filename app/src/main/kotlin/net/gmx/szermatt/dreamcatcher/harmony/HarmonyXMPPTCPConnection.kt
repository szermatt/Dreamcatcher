package net.gmx.szermatt.dreamcatcher.harmony

import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.packet.EmptyResultIQ
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.XMPPError
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.PacketParserUtils
import org.jivesoftware.smack.util.ParserUtils
import org.xmlpull.v1.XmlPullParser

/** XMPPTCPConnection that can deal with harmonyhub's idiosyncrasies. */
internal class HarmonyXMPPTCPConnection(config: XMPPTCPConnectionConfiguration?) :
    XMPPTCPConnection(config) {
    @Throws(Exception::class)
    override fun parseAndProcessStanza(parser: XmlPullParser) {
        ParserUtils.assertAtStartTag(parser)
        val parserDepth = parser.depth
        val stanza = try {
            if (IQ.IQ_ELEMENT == parser.name && parser.getAttributeValue("", "type") == null) {
                // Acknowledgement IQs don't contain a type so an empty result is created here to prevent a parsing NPE
                EmptyResultIQ()
            } else {
                PacketParserUtils.parseStanza(parser)
            }
        } catch (e: Exception) {
            val content = PacketParserUtils.parseContentDepth(parser, parserDepth)
            throw Exception("Smack message parsing exception. Content: '$content'", e)
        }
        stanza?.let { processStanza(it) }
    }

    @Throws(NotConnectedException::class, InterruptedException::class)
    override fun sendStanza(stanza: Stanza) {
        if (stanza.error == null || stanza.error.condition != XMPPError.Condition.service_unavailable) {
            super.sendStanza(stanza)
        }
    }
}