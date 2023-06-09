package net.gmx.szermatt.dreamcatcher.harmony

import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.packet.Bind
import org.jivesoftware.smack.provider.IQProvider
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/** A BindIQProvider that can deal with Harmony's JID missing a localpart (user). */
internal class HarmonyBindIQProvider : IQProvider<Bind?>() {
    @Throws(XmlPullParserException::class, IOException::class, SmackException::class)
    override fun parse(parser: XmlPullParser, initialDepth: Int): Bind? {
        var name: String?
        var bind: Bind? = null
        outerloop@ while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    name = parser.name
                    when (name) {
                        "resource" -> {
                            val resourceString = parser.nextText()
                            bind = Bind.newSet(Resourcepart.from(resourceString))
                        }
                        "jid" -> {
                            val fullJid = JidCreate.entityFullFrom("client@" + parser.nextText())
                            bind = Bind.newResult(fullJid)
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.depth == initialDepth) {
                    break@outerloop
                }
            }
        }
        return bind
    }
}