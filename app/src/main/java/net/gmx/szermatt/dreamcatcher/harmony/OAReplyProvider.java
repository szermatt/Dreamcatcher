package net.gmx.szermatt.dreamcatcher.harmony;

import static java.lang.String.format;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

public class OAReplyProvider extends IQProvider<IQ> {
    private static Map<String, OAReplyParser> replyParsers = new HashMap<>();
    static {
        replyParsers.put(MessageAuth.MIME_TYPE, new MessageAuth.AuthReplyParser());
        replyParsers.put(MessageStartActivity.MIME_TYPE, new MessageStartActivity.StartActivityReplyParser());
        replyParsers.put(MessageStartActivity.MIME_TYPE2, new MessageStartActivity.StartActivityReplyParser());
    }

    @Override
    public IQ parse(XmlPullParser parser, int initialDepth) throws Exception {
        String elementName = parser.getName();

        Map<String, String> attrs = new HashMap<>();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String prefix = parser.getAttributePrefix(i);
            if (prefix != null) {
                attrs.put(prefix + ":" + parser.getAttributeName(i), parser.getAttributeValue(i));
            } else {
                attrs.put(parser.getAttributeName(i), parser.getAttributeValue(i));
            }
        }
        String statusCode = attrs.get("errorcode");
        String errorString = attrs.get("errorstring");

        String mimeType = parser.getAttributeValue(null, "mime");
        OAReplyParser replyParser = replyParsers.get(mimeType);
        if (replyParser == null) {
            throw new HarmonyProtocolException(format("Unable to handle reply type '%s'", mimeType));
        }
        if (!replyParser.validResponseCode(statusCode)) {
            throw new HarmonyProtocolException(
                    format("Got error response [%s]: %s", statusCode, attrs.get("errorstring")));
        }

        StringBuilder contents = new StringBuilder();
        boolean done = false;
        while (!done) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    if (parser.getName().equals(elementName)) {
                        done = true;
                        break;
                    }
                    // otherwise fall through to default
                default:
                    contents.append(parser.getText());
                    break;
            }
        }
        return replyParser.parseReplyContents(statusCode, errorString, contents.toString());
    }

}
