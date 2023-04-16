package net.gmx.szermatt.dreamcatcher.harmony;


import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.ParserUtils;
import org.xmlpull.v1.XmlPullParser;

class HarmonyXMPPTCPConnection extends XMPPTCPConnection {

    public HarmonyXMPPTCPConnection(XMPPTCPConnectionConfiguration config) {
        super(config);
    }

    @Override
    protected void parseAndProcessStanza(XmlPullParser parser) throws Exception {
        ParserUtils.assertAtStartTag(parser);
        int parserDepth = parser.getDepth();
        Stanza stanza = null;
        try {
            if (IQ.IQ_ELEMENT.equals(parser.getName()) && parser.getAttributeValue("", "type") == null) {
                // Acknowledgement IQs don't contain a type so an empty result is created here to prevent a parsing NPE
                stanza = new EmptyResultIQ();
            } else {
                stanza = PacketParserUtils.parseStanza(parser);
            }
        } catch (Exception e) {
            CharSequence content = PacketParserUtils.parseContentDepth(parser, parserDepth);
            throw new Exception("Smack message parsing exception. Content: '" + content + "'", e);
        }
        if (stanza != null) {
            processStanza(stanza);
        }
    }

    @Override
    public void sendStanza(Stanza stanza) throws NotConnectedException, InterruptedException {
        if (stanza.getError() == null || stanza.getError().getCondition() != Condition.service_unavailable) {
            super.sendStanza(stanza);
        }
    }

}
