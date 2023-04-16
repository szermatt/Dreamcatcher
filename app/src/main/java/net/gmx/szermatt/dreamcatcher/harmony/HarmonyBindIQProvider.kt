package net.gmx.szermatt.dreamcatcher.harmony;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Bind;
import org.jivesoftware.smack.provider.IQProvider;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/*
 * Copied from BindIQProvider, but tweaked to support the Harmony's JID that does not have a localpart (user)
 */
class HarmonyBindIQProvider extends IQProvider<Bind> {

    @Override
    public Bind parse(XmlPullParser parser, int initialDepth)
            throws XmlPullParserException, IOException, SmackException {
        String name;
        Bind bind = null;
        outerloop:
        while (true) {
            int eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    name = parser.getName();
                    switch (name) {
                        case "resource":
                            String resourceString = parser.nextText();
                            bind = Bind.newSet(Resourcepart.from(resourceString));
                            break;
                        case "jid":
                            EntityFullJid fullJid = JidCreate.entityFullFrom("client@" + parser.nextText());
                            bind = Bind.newResult(fullJid);
                            break;
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
            }
        }
        return bind;
    }

}
