package net.gmx.szermatt.dreamcatcher.harmony

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jivesoftware.smack.util.PacketParserUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
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
                    println("running task")
                    task.run()
                    println("task done")
                }


                println("running test; read from auth")
                val parser = PacketParserUtils.getParserFor(
                    InputStreamReader(
                        authSocket.output.inputStream,
                        Charsets.UTF_8
                    )
                )
                assertEquals(XmlPullParser.START_TAG, parser.eventType)
                assertEquals("stream", parser.name)
                assertEquals("http://etherx.jabber.org/streams", parser.namespace)

                println("write to auth")
                val writer =
                    OutputStreamWriter(authSocket.input.outputStream, Charsets.ISO_8859_1)
                writer.write(
                    """<?xml version='1.0' encoding='iso-8859-1'?>
                <stream:stream from='harmonyhub' id='068adbb1' version='1.0' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>
                <stream:features>
                    <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
                        <mechanism>PLAIN</mechanism>
                    </mechanisms>
                </stream:features>""".trimIndent()
                )
                writer.flush()
                println("written stream")



                println("got stream")



                println("got tag")
                parser.nextTag()
                assertEquals(XmlPullParser.START_TAG, parser.eventType)
                assertEquals("auth", parser.name)
                assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", parser.namespace)
                println("got auth")
            }

        } finally {
            threadPool.shutdown()
        }
    }

    private fun initSocketImpl() {
        synchronized(classLock) {
            if (socketImplSetup) {
                return
            }
            Socket.setSocketImplFactory(FakeSocketImplFactory {
                sockets.poll(10, TimeUnit.SECONDS)
                    ?: throw IllegalStateException("No FakeSocket available")
            })
            socketImplSetup = true
        }
    }

    companion object {
        val classLock = Any() // protects fields below
        val sockets = ArrayBlockingQueue<FakeSocket>(10)
        var socketImplSetup = false
    }
}