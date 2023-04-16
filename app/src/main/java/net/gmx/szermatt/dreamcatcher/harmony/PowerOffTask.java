package net.gmx.szermatt.dreamcatcher.harmony;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.android.AndroidSmackInitializer;
import org.jivesoftware.smack.packet.Bind;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Resourcepart;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.ReentrantLock;

/** Sends a power off command to the Harmony hub. */
public class PowerOffTask {
    public static final int DEFAULT_REPLY_TIMEOUT = 5000;
    public static final int START_ACTIVITY_REPLY_TIMEOUT = 30_000;
    private static final int DEFAULT_PORT = 5222;
    private static final String DEFAULT_XMPP_USER = "guest@connect.logitech.com/gatorade.";
    private static final String DEFAULT_XMPP_PASSWORD = "gatorade.";

    /**
     * True once smack has been initialized in the current VM.
     */
    @GuardedBy("PowerOffTask.class")
    private static boolean initialized;

    /**
     * To prevent timeouts when different threads send a message and expect a response, create a lock that only allows a
     * single thread at a time to perform a send/receive action.
     */
    private final ReentrantLock messageLock = new ReentrantLock();

    @GuardedBy("this")
    private boolean stopped;

    /** Initialize smack. This must be called at least once. */
    private static synchronized void init() {
        if (initialized) {
            return;
        }
        new AndroidSmackInitializer().initialize();
        ProviderManager.addIQProvider(Bind.ELEMENT, Bind.NAMESPACE, new HarmonyBindIQProvider());
        ProviderManager.addIQProvider("oa", "connect.logitech.com", new OAReplyProvider());
        initialized = true;
    }

    /** Stops communicating with the server at the next convenient point. */
    public synchronized void stop() {
        if (!stopped) {
            stopped = true;
        }
    }

    /** Returns true if stop() was called. */
    public synchronized boolean isStopped() {
        return stopped;
    }

    /**
     * Connects to the Harmony Hub and send the power off command.
     *
     * @throws CancellationException if the task was stopped before it could be completed
     */
    public void run() throws Exception {
        init();
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getByName("192.168.1.116"))
                .setPort(DEFAULT_PORT)
                .setXmppDomain("harmonyhub.zia")
                .addEnabledSaslMechanism(SASLMechanism.PLAIN)
                .build();
        MessageAuth.AuthReply authReply = authenticate(config);
        powerOff(config, authReply.getUsername(), authReply.getPassword());
    }

    /**
     * Gets the session auth credentials.
     *
     * @return null if stopped, the credentials otherwise
     */
    @NonNull
    private MessageAuth.AuthReply authenticate(XMPPTCPConnectionConfiguration config)
            throws SmackException, IOException, XMPPException, InterruptedException,
            CancellationException {
        XMPPTCPConnection connection = new HarmonyXMPPTCPConnection(config);
        try {
            cancelIfStopped();
            connection.connect();
            cancelIfStopped();
            connection.login(DEFAULT_XMPP_USER, DEFAULT_XMPP_PASSWORD, Resourcepart.from("auth"));
            cancelIfStopped();

            connection.setFromMode(XMPPConnection.FromMode.USER);
            return sendOAStanza(
                    connection, new MessageAuth.AuthRequest(), MessageAuth.AuthReply.class, DEFAULT_REPLY_TIMEOUT);
        } finally {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
    }

    /**
     * Connect with the given credentials and send the power off command.
     */
    private void powerOff(XMPPTCPConnectionConfiguration config, String userName, String password)
            throws SmackException, IOException, XMPPException, InterruptedException, CancellationException {
        XMPPTCPConnection connection = new HarmonyXMPPTCPConnection(config);
        try {
            cancelIfStopped();
            connection.connect();
            cancelIfStopped();
            connection.login(userName, password, Resourcepart.from("main"));
            cancelIfStopped();
            connection.setFromMode(XMPPConnection.FromMode.USER);
            sendOAStanza(
                    connection,
                    new MessageStartActivity.StartActivityRequest(-1),
                    MessageStartActivity.StartActivityReply.class,
                    START_ACTIVITY_REPLY_TIMEOUT);
        } finally {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private <R extends OAStanza> R sendOAStanza(XMPPTCPConnection connection, OAStanza stanza, Class<R> replyClass,
                                                long replyTimeout) {
        StanzaCollector collector = connection.createStanzaCollector(new OAReplyFilter(stanza, connection));
        messageLock.lock();
        try {
            connection.sendStanza(stanza);
            return replyClass.cast(getNextStanzaSkipContinues(collector, replyTimeout, connection));
        } catch (InterruptedException | SmackException | XMPPErrorException e) {
            throw new RuntimeException("Failed communicating with Harmony Hub", e);
        } finally {
            messageLock.unlock();
            collector.cancel();
        }
    }

    @NonNull
    private Stanza getNextStanzaSkipContinues(
            StanzaCollector collector, long replyTimeout, XMPPTCPConnection connection)
            throws InterruptedException, NoResponseException, XMPPErrorException {
        while (true) {
            Stanza reply = collector.nextResult(replyTimeout);
            if (reply == null) {
                throw NoResponseException.newWith(connection, collector);
            }
            if (reply instanceof OAStanza && ((OAStanza) reply).isContinuePacket()) {
                continue;
            }
            return reply;
        }
    }

    private void cancelIfStopped() throws CancellationException {
        if (isStopped()) {
            throw new CancellationException("stopped");
        }
    }
}
