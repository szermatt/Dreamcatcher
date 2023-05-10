package net.gmx.szermatt.dreamcatcher.harmony

import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import kotlinx.coroutines.*
import net.gmx.szermatt.dreamcatcher.DreamcatcherApplication.Companion.TAG
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
import kotlin.time.Duration.Companion.seconds

/** Sends a power off command to the Harmony hub.  */
class PowerOffTask(
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

    /**
     * Connects to the Harmony Hub and send the power off command.
     *
     * @throws CancellationException if the task was stopped before it could be completed
     */
    @ExperimentalCoroutinesApi
    suspend fun run(
        host: String? = null,
        uuid: String? = null,
        port: Int = 5222,
        dryRun: Boolean = false
    ) {
        reportProgress(PowerOffStep.STEP_STARTED, dryRun)
        init()

        ensureActive()
        val address = withTimeout(30.seconds) {
            getAddress(host, uuid)
        }
        Log.i(TAG, "Connecting to Harmony Hub on ${address}")
        val config = buildConfig(address, port)
        reportProgress(PowerOffStep.STEP_RESOLVED, dryRun)

        ensureActive()
        val identity = obtainSessionToken(config, dryRun)

        reportProgress(PowerOffStep.STEP_AUTH_DONE, dryRun)
        if (dryRun) return

        ensureActive()
        powerOff(config, identity)
        reportProgress(PowerOffStep.STEP_DONE, dryRun)
    }

    /**
     * Returns the IP address to connect to.
     *
     * If [host] is null, getAddress uses discovery to find a Harmony Hub and either
     * uses the first one, if [uuid] is null, or the one with the specified UUID.
     *
     * @throws UnknownHostException if [host] is invalid or no Hub with the specified UUId answered
     */
    @ExperimentalCoroutinesApi
    private suspend fun CoroutineScope.getAddress(host: String?, uuid: String?): InetAddress {
        if (host != null) {
            return InetAddress.getByName(host)
        }

        val c = discoveryChannel()
        try {
            for (hub in c) {
                if (uuid != null && hub.uuid != hub.uuid) continue

                return InetAddress.getByName(hub.ip)
            }
            throw UnknownHostException("No hub found")
        } finally {
            c.cancel()
        }
    }

    /**
     * Builds the [XMPPTCPConnectionConfiguration] given the constructor parameters.
     *
     * @throws java.net.UnknownHostException if the given hostname cannot be resolved
     */
    private fun buildConfig(address: InetAddress, port: Int): XMPPTCPConnectionConfiguration {
        return XMPPTCPConnectionConfiguration.builder()
            .setHostAddress(address)
            .setPort(port)
            .setXmppDomain("harmonyhub")
            .addEnabledSaslMechanism(SASLMechanism.PLAIN)
            .build()
    }

    /** Obtain a session token to login to the harmony hub. */
    @Throws(Exception::class)
    private suspend fun obtainSessionToken(
        config: XMPPTCPConnectionConfiguration, dryRun: Boolean
    ): String {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        try {
            connection.addConnectionListener(object : AbstractConnectionListener() {
                override fun connected(connection: XMPPConnection?) {
                    reportProgress(PowerOffStep.STEP_AUTH_CONNECTED, dryRun)
                }
            })
            ensureActive()
            connection.connect()
            ensureActive()
            connection.login(
                "${XMPP_USER_NAME}@${XMPP_USER_DOMAIN}",
                XMPP_USER_PASSWORD,
                Resourcepart.from("auth")
            )

            ensureActive()
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
    private suspend fun powerOff(
        config: XMPPTCPConnectionConfiguration, sessionToken: String
    ) {
        val connection: XMPPTCPConnection = HarmonyXMPPTCPConnection(config)
        try {
            connection.addConnectionListener(object : AbstractConnectionListener() {
                override fun connected(connection: XMPPConnection?) {
                    reportProgress(PowerOffStep.STEP_MAIN_CONNECTED, dryRun = false)
                }
            })
            ensureActive()
            connection.connect()
            ensureActive()
            connection.login(
                "${sessionToken}@${XMPP_USER_DOMAIN}",
                sessionToken,
                Resourcepart.from("main")
            )
            ensureActive()
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

    /**
     * Checks whether the job has been cancelled and if yes, throws a cancellation exception.
     */
    private suspend fun ensureActive() {
        currentCoroutineContext().ensureActive()
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