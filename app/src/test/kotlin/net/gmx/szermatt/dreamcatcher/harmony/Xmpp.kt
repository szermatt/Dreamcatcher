package net.gmx.szermatt.dreamcatcher.harmony

import org.jivesoftware.smack.util.PacketParserUtils
import org.junit.Assert
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/** Test helper for parsing an XMPP input stream. */
class XmppTestParser(inputStream: InputStream, charset: Charset) {
    private val reader = InputStreamReader(inputStream, charset)
    private val parser = PacketParserUtils.newXmppParser(reader)

    /** Expects an auth tag and answers it successfully, no matter what it contains. */
    fun processAuth(writer: XmppTestWriter, lambda: (() -> Unit)? = null) =
        consumeTag("auth", "urn:ietf:params:xml:ns:xmpp-sasl") {
            if (lambda != null) lambda()
            writer.send("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>")
        }

    /** Expects a bind IQ tag and answers it. */
    fun processBind(writer: XmppTestWriter, resource: String) = consumeIq("set") { id ->
        consumeTag("bind", "urn:ietf:params:xml:ns:xmpp-bind")
        writer.send(
            """<iq id='$id' type='result'>
                            <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>
                                <jid>1111/$resource</jid>
                            </bind>
                        </iq>"""
        )
    }

    /** Expects a session IQ tag and answers it. */
    fun processSession(writer: XmppTestWriter) = consumeIq("set") { id ->
        consumeTag("session", "urn:ietf:params:xml:ns:xmpp-session")
        writer.send("<iq id='$id' type='result'></iq>")
    }

    /** Expects a query roster IQ tag and answers it. */
    fun processRoster(writer: XmppTestWriter) = consumeIq("get") { id ->
        consumeTag("query", "jabber:iq:roster")
        writer.send("<iq id='$id' to='client@1111/auth' type='result'><query xmlns='jabber:iq:roster' ver='ver7'/></iq>")
    }

    /** Expects a presence IQ tag and answers it. */
    fun processPresence(writer: XmppTestWriter) = consumeTag("presence", "jabber:client") {
        // The Harmony XMPP server answers presence with an empty IQ tag
        writer.send("<iq/>")
    }

    /** Moves to the next tag, which must be an `iq` tag of the given type and executes [lambda] */
    fun consumeIq(type: String, lambda: ((id: String) -> Unit)? = null) {
        consumeTag("iq", "jabber:client") {
            val id = parser.getAttributeValue("", "id")
            Assert.assertEquals(type, parser.getAttributeValue("", "type"))
            if (lambda != null) lambda(id)
        }
    }

    /** Moves to the next tag, which must be the one specified and executes [lambda] */
    fun consumeTag(localName: String, namespace: String? = null, lambda: (() -> Unit)? = null) {
        skipToTagStart()
        val depth = parser.depth
        expectStartTag(localName, namespace)
        if (lambda != null) lambda()
        skipToTagEnd(depth)
    }

    /** Expects that the parser is positioned on a `stream` start tag. */
    fun expectOpenStream() {
        skipToTagStart()
        expectStartTag("stream", "http://etherx.jabber.org/streams")
    }

    /** Expects the current stream to be closed. */
    private fun expectCloseStream(depth: Int) {
        skipToTagEnd(depth)
        expectEndTag("stream", "http://etherx.jabber.org/streams")
    }

    /** Expects a stream and process it by executing [lambda]. */
    fun consumeStream(close: Boolean, lambda: () -> Unit) {
        expectOpenStream()
        val depth = parser.depth
        lambda()
        if (close) expectCloseStream(depth)
    }

    /** Expects that the parser is positioned on the specified tag. */
    fun expectStartTag(localName: String, namespace: String? = null) {
        Assert.assertEquals(XmlPullParser.START_TAG, parser.eventType)
        Assert.assertEquals(localName, parser.name)
        if (namespace != null) {
            Assert.assertEquals(namespace, parser.namespace)
        }
    }

    /** Expects that the parser is positioned on the specified tag end. */
    fun expectEndTag(localName: String, namespace: String? = null) {
        Assert.assertEquals(XmlPullParser.END_TAG, parser.eventType)
        Assert.assertEquals(localName, parser.name)
        if (namespace != null) {
            Assert.assertEquals(namespace, parser.namespace)
        }
    }

    /** Moves the parser to the start of the next tag. */
    fun skipToTagStart() {
        parser.nextTag()
    }

    /** Moves the parser to the end of the tag at the given [depth]. */
    fun skipToTagEnd(depth: Int) {
        while (true) {
            when (parser.eventType) {
                XmlPullParser.END_TAG -> {
                    if (depth == parser.depth) return
                }
                XmlPullParser.END_DOCUMENT ->
                    throw IllegalStateException(
                        "Reached end of the document without finding the end tag"
                    )
            }
            parser.next()
        }
    }

    /** Returns the text content of the current tag or the empty string. */
    fun consumeTextContent(): String {
        if (parser.eventType == XmlPullParser.START_TAG) {
            parser.next()
        }
        val sb = StringBuilder()
        while (true) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    return sb.toString()
                }
                else -> throw IllegalStateException("Unexpected even type ${parser.eventType}")
            }
            parser.next()
        }
    }
}

/** A helper for writing XMPP output streamss. */
class XmppTestWriter(outputStream: OutputStream, charset: Charset) {
    private val writer = OutputStreamWriter(outputStream, charset)

    init {
        send("<?xml version='1.0' encoding='${charset.name()}'?>")
    }

    /** Sends the given text. */
    fun send(text: String) {
        writer.write(text.trimIndent())
        writer.flush()
    }

    /** Opens a stream tag and includes the given [features] into it right away. */
    fun openStreamWithFeatures(features: String) {
        send(
            """<stream:stream 
                        from='harmonyhub' 
                        id='068adbb1' 
                        version='1.0' 
                        xmlns='jabber:client' 
                        xmlns:stream='http://etherx.jabber.org/streams'>
                <stream:features>${features.trimIndent()}</stream:features>"""
        )
    }

    fun openStreamWithPlainAuth() = openStreamWithFeatures(
        """<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
                            <mechanism>PLAIN</mechanism>
                        </mechanisms>"""
    )

    fun openStreamWithSession() = openStreamWithFeatures(
        """<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>
                             <session xmlns='urn:ietf:params:xml:nx:xmpp-session'/>"""
    )

    fun closeStream() {
        writer.write("</stream:stream>")
    }

    fun close() {
        writer.close()
    }
}