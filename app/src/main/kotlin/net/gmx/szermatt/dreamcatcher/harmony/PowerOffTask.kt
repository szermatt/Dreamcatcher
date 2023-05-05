package net.gmx.szermatt.dreamcatcher.harmony

import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import net.gmx.szermatt.dreamcatcher.DreamCatcherApplication.Companion.TAG
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.MAX_DRY_RUN_STEP
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.MAX_STEP
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_AUTH_CONNECTED
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_AUTH_DONE
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_DONE
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_MAIN_CONNECTED
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_RESOLVED
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_SCHEDULED
import net.gmx.szermatt.dreamcatcher.harmony.PowerOffStep.Companion.STEP_STARTED
import org.jivesoftware.smack.AbstractConnectionListener
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.StanzaCollector
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.packet.Bind
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.sasl.SASLMechanism
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.parts.Resourcepart
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.locks.ReentrantLock

/** Sends a power off command to the Harmony hub.  */
class PowerOffTask(
    private val host: String? = null,
    private val uuid: String? = null,
    private val port: Int = 5222,
    private val listener: Listener? = null,
) {
    /** A listener can be told about the progress of the task. */
    interface Listener {
        fun onPowerOffTaskProgress(@PowerOffStep step: Int, stepCount: Int);
    }

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
    fun run(dryRun: Boolean = false) {
        reportProgress(PowerOffStep.STEP_STARTED, dryRun)
        init()
        val address = if (host != null) {
            InetAddress.getByName(host)
        } else {
            discoverHub(uuid = uuid) ?: throw UnknownHostException("No Harmony Hub found")
        }
        Log.i(TAG, "Connecting to Harmony Hub on ${address}")
        val config = buildConfig(address)
        reportProgress(PowerOffStep.STEP_RESOLVED, dryRun)

        val identity = obtainSessionToken(config, dryRun)
        reportProgress(PowerOffStep.STEP_AUTH_DONE, dryRun)
        if (dryRun) return

        powerOff(config, identity)
        reportProgress(PowerOffStep.STEP_DONE, dryRun)
    }

    /**
     * Builds the [XMPPTCPConnectionConfiguration] given the constructor parameters.
     *
     * @throws java.net.UnknownHostException if the given hostname cannot be resolved
     */
    private fun buildConfig(address: InetAddress): XMPPTCPConnectionConfiguration {
        // Note that hostname resolution is done here, because Smack's DNS resolution
        // doesn't always follow the local DNS configuration.
        return XMPPTCPConnectionConfiguration.builder()
            .setHostAddress(address)
            .setPort(port)
            .setXmppDomain("harmonyhub")
            .addEnabledSaslMechanism(SASLMechanism.PLAIN)
            .build()
    }

    /** Obtain a session token to login to the harmony hub. */
    @Throws(Exception::class)
    private fun obtainSessionToken(
        config: XMPPTCPConnectionConfiguration, dryRun: Boolean
    ): String {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        try {
            connection.addConnectionListener(object : AbstractConnectionListener() {
                override fun connected(connection: XMPPConnection?) {
                    reportProgress(PowerOffStep.STEP_AUTH_CONNECTED, dryRun)
                }
            })
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
            val reply = sendOAStanza(
                connection,
                PairRequest(),
                PairReply::class.java,
                DEFAULT_REPLY_TIMEOUT.toLong()
            )
            return reply?.identity
                ?: throw HarmonyProtocolException("Session authentication failed")
        } finally {
            if (connection.isConnected) {
                connection.disconnect()
            }
        }
    }

    /**
     * Connect with the given session token and send the power off command.
     */
    @Throws(Exception::class)
    private fun powerOff(
        config: XMPPTCPConnectionConfiguration, sessionToken: String
    ) {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        try {
            connection.addConnectionListener(object : AbstractConnectionListener() {
                override fun connected(connection: XMPPConnection?) {
                    reportProgress(PowerOffStep.STEP_MAIN_CONNECTED, dryRun = false)
                }
            })
            cancelIfStopped()
            connection.connect()
            cancelIfStopped()
            connection.login(
                "${sessionToken}@${XMPP_USER_DOMAIN}",
                sessionToken,
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

    @Throws(Exception::class)
    private fun <R : OAStanza?> sendOAStanza(
        connection: XMPPTCPConnection, stanza: OAStanza, replyClass: Class<R>,
        replyTimeout: Long
    ): R? {
        val collector = connection.createStanzaCollector(OAReplyFilter(stanza, connection))
        mMessageLock.lock()
        return try {
            connection.sendStanza(stanza)
            replyClass.cast(getNextStanzaSkipContinues(collector, replyTimeout, connection))
        } finally {
            mMessageLock.unlock()
            collector.cancel()
        }
    }

    @Throws(Exception::class)
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

    /** Reports having progressed to the given step. */
    private fun reportProgress(@PowerOffStep step: Int, dryRun: Boolean) {
        listener?.onPowerOffTaskProgress(
            step, if (dryRun) {
                MAX_DRY_RUN_STEP
            } else {
                MAX_STEP
            }
        )
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

@IntDef(STEP_SCHEDULED, STEP_STARTED, STEP_RESOLVED, STEP_AUTH_CONNECTED, STEP_AUTH_DONE, STEP_MAIN_CONNECTED, STEP_DONE)
@Retention(AnnotationRetention.SOURCE)
annotation class PowerOffStep {
    companion object {
        const val STEP_SCHEDULED = 0
        const val STEP_STARTED = 10
        const val STEP_RESOLVED = 20
        const val STEP_AUTH_CONNECTED = 30
        const val STEP_AUTH_DONE = 40
        const val STEP_MAIN_CONNECTED = 50
        const val STEP_DONE = 60

        const val MAX_DRY_RUN_STEP = STEP_AUTH_DONE
        const val MAX_STEP = STEP_DONE
    }
}