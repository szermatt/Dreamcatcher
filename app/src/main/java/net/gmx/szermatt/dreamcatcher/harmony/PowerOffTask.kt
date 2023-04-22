package net.gmx.szermatt.dreamcatcher.harmony

import androidx.annotation.GuardedBy
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.StanzaCollector
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.packet.Bind
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.sasl.SASLMechanism
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.parts.Resourcepart
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock

/** Sends a power off command to the Harmony hub.  */
class PowerOffTask(
    private val host: String = "192.168.1.116",
    private val port: Int = 5222
) {
    /**
     * To prevent timeouts when different threads send a message and expect a response, create a lock that only allows a
     * single thread at a time to perform a send/receive action.
     */
    private val mMessageLock = ReentrantLock()

    /** Returns true if stop() was called.  */
    @get:Synchronized
    @GuardedBy("this")
    var isStopped = false
        private set

    /** Stops communicating with the server at the next convenient point.  */
    @Synchronized
    fun stop() {
        if (!isStopped) {
            isStopped = true
        }
    }

    /**
     * Connects to the Harmony Hub and send the power off command.
     *
     * @throws CancellationException if the task was stopped before it could be completed
     */
    @Throws(Exception::class)
    fun run() {
        init()
        val config = XMPPTCPConnectionConfiguration.builder()
            .setHostAddress(InetAddress.getByName(host))
            .setPort(port)
            .setXmppDomain("harmonyhub")
            .addEnabledSaslMechanism(SASLMechanism.PLAIN)
            .build()
        val identity = obtainSessionToken(config)?.identity
            ?: throw HarmonyProtocolException("Session authentication failed")
        powerOff(config, identity)
    }

    /** Obtain a session token to login to the harmony hub. */
    @Throws(
        SmackException::class,
        IOException::class,
        XMPPException::class,
        InterruptedException::class,
        CancellationException::class
    )
    private fun obtainSessionToken(config: XMPPTCPConnectionConfiguration): PairReply? {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        return try {
            cancelIfStopped()
            connection.connect()
            cancelIfStopped()
            connection.login(
                "${XMPP_USER_NAME}@${XMPP_USER_DOMAIN}",
                XMPP_USER_PASSWORD,
                Resourcepart.from("auth")
            )
            cancelIfStopped()
            connection.fromMode = XMPPConnection.FromMode.USER
            sendOAStanza(
                connection,
                PairRequest(),
                PairReply::class.java,
                DEFAULT_REPLY_TIMEOUT.toLong()
            )
        } finally {
            if (connection.isConnected) {
                connection.disconnect()
            }
        }
    }

    /**
     * Connect with the given credentials and send the power off command.
     */
    @Throws(
        SmackException::class,
        IOException::class,
        XMPPException::class,
        InterruptedException::class,
        CancellationException::class
    )
    private fun powerOff(
        config: XMPPTCPConnectionConfiguration,
        identity: String,
    ) {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        try {
            cancelIfStopped()
            connection.connect()
            cancelIfStopped()
            connection.login(
                "${identity}@${XMPP_USER_DOMAIN}",
                identity,
                Resourcepart.from("main")
            )
            cancelIfStopped()
            connection.fromMode = XMPPConnection.FromMode.USER
            sendOAStanza(
                connection,
                StartActivityRequest(-1),
                StartActivityReply::class.java,
                START_ACTIVITY_REPLY_TIMEOUT.toLong()
            )
        } finally {
            if (connection.isConnected) {
                connection.disconnect()
            }
        }
    }

    private fun <R : OAStanza?> sendOAStanza(
        connection: XMPPTCPConnection, stanza: OAStanza, replyClass: Class<R>,
        replyTimeout: Long
    ): R? {
        val collector = connection.createStanzaCollector(OAReplyFilter(stanza, connection))
        mMessageLock.lock()
        return try {
            connection.sendStanza(stanza)
            replyClass.cast(getNextStanzaSkipContinues(collector, replyTimeout, connection))
        } catch (e: InterruptedException) {
            throw RuntimeException("Failed communicating with Harmony Hub", e)
        } catch (e: SmackException) {
            throw RuntimeException("Failed communicating with Harmony Hub", e)
        } catch (e: XMPPErrorException) {
            throw RuntimeException("Failed communicating with Harmony Hub", e)
        } finally {
            mMessageLock.unlock()
            collector.cancel()
        }
    }

    @Throws(InterruptedException::class, NoResponseException::class, XMPPErrorException::class)
    private fun getNextStanzaSkipContinues(
        collector: StanzaCollector, replyTimeout: Long, connection: XMPPTCPConnection
    ): Stanza {
        while (true) {
            val reply = collector.nextResult<Stanza>(replyTimeout)
                ?: throw NoResponseException.newWith(connection, collector)
            if (reply is OAStanza && reply.isContinuePacket) {
                continue
            }
            return reply
        }
    }

    @Throws(CancellationException::class)
    private fun cancelIfStopped() {
        if (isStopped) {
            throw CancellationException("stopped")
        }
    }

    companion object {
        const val DEFAULT_REPLY_TIMEOUT = 5000
        const val START_ACTIVITY_REPLY_TIMEOUT = 30000
        private const val XMPP_USER_NAME = "guest"
        private const val XMPP_USER_DOMAIN = "connect.logitech.com/gatorade"
        private const val XMPP_USER_PASSWORD = "gatorade."

        /**
         * True once smack has been initialized in the current VM.
         */
        @GuardedBy("PowerOffTask.class")
        private var mInitialized = false

        /** Initialize smack. This must be called at least once.  */
        @Synchronized
        private fun init() {
            if (mInitialized) {
                return
            }
            AndroidSmackInitializer().initialize()
            ProviderManager.addIQProvider(Bind.ELEMENT, Bind.NAMESPACE, HarmonyBindIQProvider())
            ProviderManager.addIQProvider("oa", "connect.logitech.com", OAReplyProvider())
            mInitialized = true
        }
    }
}