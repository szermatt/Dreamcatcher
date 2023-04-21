package net.gmx.szermatt.dreamcatcher.harmony

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@RunWith(RobolectricTestRunner::class)
class PowerOffTaskTest {

    @Test
    fun run() {
        initSocketImpl()
        val authSocket = FakeSocket()
        authSocket.dumpAs("auth", Charsets.UTF_8)
        sockets.add(authSocket)

        val mainSocket = FakeSocket()
        mainSocket.dumpAs("main", Charsets.UTF_8)
        sockets.add(mainSocket)

        val task = PowerOffTask("127.0.0.1")

        val threadPool = Executors.newCachedThreadPool()
        try {
            runBlocking(threadPool.asCoroutineDispatcher()) {
                launch {
                    task.run()
                }
                runAuth(authSocket)
                runMain(mainSocket)

                // TODO: check addresses
            }
        } finally {
            threadPool.shutdown()
        }
    }

    /** Simulates the XMPP request sent to obtain the session token. */
    private fun runAuth(socket: FakeSocket) {
        val parser = XmppTestParser(socket.output.inputStream, Charsets.UTF_8)
        val writer = XmppTestWriter(socket.input.outputStream, Charsets.ISO_8859_1)

        parser.consumeStream(close = false) { // The outer stream tag is never actually closed
            writer.openStreamWithPlainAuth()
            parser.processAuth(writer)
            parser.consumeStream(close = true) {
                writer.openStreamWithSession()
                parser.processBind(writer, "auth")
                parser.processSession(writer)
                parser.processRoster(writer)
                parser.processPresence(writer)
                parser.consumeIq("get") { id ->
                    parser.consumeTag("oa", "connect.logitech.com") {
                        writer.send(
                            """<iq id="$id" to="client@1111/auth" type="get"> 
                                <oa errorcode='200' errorstring='OK' mime='vnd.logitech.connect/vnd.logitech.pair' xmlns='connect.logitech.com'>
                                    <![CDATA[serverIdentity=ed23c162a01b9ef7b2729c553eb8d7c0f841f7a3:hubId=106:identity=ed23c162a01b9ef7b2729c553eb8d7c0f841f7a3:status=succeeded:protocolVersion={XMPP="1.0", HTTP="1.0", RF="1.0", WEBSOCKET="1.0"}:hubProfiles={Harmony="2.0"}:productId=Pimento:friendlyName=ia]]>                            
                                </oa>
                            </iq>"""
                        )
                    }
                }
            }
        }
        writer.close()
    }

    /** Simulates logging into XMPP using the session token, then fire the power off command. */
    private fun runMain(socket: FakeSocket) {
        val parser = XmppTestParser(socket.output.inputStream, Charsets.UTF_8)
        val writer = XmppTestWriter(socket.input.outputStream, Charsets.ISO_8859_1)
        parser.consumeStream(close = false) { // The outer stream tag is never actually closed
            writer.openStreamWithPlainAuth()
            parser.processAuth(writer) // TODO: check auth credentials
            parser.consumeStream(close = true) {
                writer.openStreamWithSession()
                parser.processBind(writer, "main")
                parser.processSession(writer)
                parser.processRoster(writer)
                parser.processPresence(writer)
                parser.consumeIq("get") { id ->
                    parser.consumeTag("oa", "connect.logitech.com") {
                        // TODO: check OA power off command
                        writer.send(
                            """<iq id="$id" to="client@1111/main" type="get">
                <oa errorcode='200'
                    errorstring='OK'
                    mime='vnd.logitech.harmony/vnd.logitech.harmony.engine?startactivity' xmlns='connect.logitech.com'></oa>
            </iq>"""
                        )
                    }
                }
            }
        }
        writer.close()
    }

    private fun skipToEndTag(parser: XmlPullParser) {
        var depth = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    if (depth == 0) return
                    depth--
                }
                XmlPullParser.END_DOCUMENT ->
                    throw IllegalStateException("unexpected END_DOCUMENT")
            }
        }
    }

    private fun initSocketImpl() {
        synchronized(classLock) {
            if (socketImplSetup) {
                return
            }
            initFakeSockets {
                sockets.poll(10, TimeUnit.SECONDS)
                    ?: throw IllegalStateException("No FakeSocket available")
            }
            socketImplSetup = true
        }
    }

    companion object {
        val classLock = Any() // protects fields below
        val sockets = ArrayBlockingQueue<FakeSocket>(10)
        var socketImplSetup = false
    }
}