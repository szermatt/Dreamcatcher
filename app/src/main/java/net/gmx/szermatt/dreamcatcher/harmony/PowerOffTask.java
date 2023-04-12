package net.gmx.szermatt.dreamcatcher.harmony;

import android.util.Log;

import androidx.annotation.GuardedBy;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.android.AndroidSmackInitializer;
import org.jivesoftware.smack.packet.Bind;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Resourcepart;

import java.net.InetAddress;
import java.util.concurrent.locks.ReentrantLock;

public class PowerOffTask  {

    private static final String TAG = "Harmony";

  public static final int DEFAULT_REPLY_TIMEOUT = 5000; // 30_000;
  public static final int START_ACTIVITY_REPLY_TIMEOUT = 30_000;
  private static final int DEFAULT_PORT = 5222;
  private static final String DEFAULT_XMPP_USER = "guest@connect.logitech.com/gatorade.";
  private static final String DEFAULT_XMPP_PASSWORD = "gatorade.";

  /** True once smack has been initialized in the current VM. */
  @GuardedBy("PowerOffTask.class")
  private static boolean initialized;

    /**
     * To prevent timeouts when different threads send a message and expect a response, create a lock that only allows a
     * single thread at a time to perform a send/receive action.
     */
  private ReentrantLock messageLock = new ReentrantLock();


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

  public void run() throws Exception {
    init();
    XMPPTCPConnectionConfiguration connectionConfig = XMPPTCPConnectionConfiguration.builder()
        .setHostAddress(InetAddress.getByName("192.168.1.116"))
        .setPort(DEFAULT_PORT)
        .setXmppDomain("harmonyhub.zia")
        .addEnabledSaslMechanism(SASLMechanism.PLAIN)
        .build();
    HarmonyXMPPTCPConnection authConnection = new HarmonyXMPPTCPConnection(connectionConfig);
    Log.i(TAG, "connect...");
    authConnection.connect();
    Log.i(TAG, "connected.");
    Log.i(TAG, "login...");
    authConnection.login(DEFAULT_XMPP_USER, DEFAULT_XMPP_PASSWORD, Resourcepart.from("auth"));
    Log.i(TAG, "logged in.");
    authConnection.setFromMode(XMPPConnection.FromMode.USER);

    Log.i(TAG, "auth...");
    MessageAuth.AuthRequest sessionRequest = new MessageAuth.AuthRequest();
    MessageAuth.AuthReply oaResponse = sendOAStanza(authConnection, sessionRequest, MessageAuth.AuthReply.class);

    Log.i(TAG, "auth ok: " + oaResponse.getUsername() + " " + oaResponse.getPassword());
    Log.i(TAG, "disconnect...");
    authConnection.disconnect();
    Log.i(TAG, "disconnected.");

    Log.i(TAG, "reconnect...");
    HarmonyXMPPTCPConnection connection = new HarmonyXMPPTCPConnection(connectionConfig);
    connection.connect();
    Log.i(TAG, "reconnected.");
    Log.i(TAG, "re-auth...");
    connection.login(oaResponse.getUsername(), oaResponse.getPassword(), Resourcepart.from("main"));
    Log.i(TAG, "re-auth ok.");
    connection.setFromMode(XMPPConnection.FromMode.USER);
    Log.i(TAG, "power off...");
    sendOAStanza(connection, new MessageStartActivity.StartActivityRequest(-1), MessageStartActivity.StartActivityReply.class, START_ACTIVITY_REPLY_TIMEOUT);
    Log.i(TAG, "power off...done");

    Log.i(TAG, "disconnect...");
    connection.disconnect();
    Log.i(TAG, "disconnected.");
  }

    private Stanza sendOAStanza(XMPPTCPConnection authConnection, OAStanza stanza) {
        return sendOAStanza(authConnection, stanza, DEFAULT_REPLY_TIMEOUT);
    }

    private Stanza sendOAStanza(XMPPTCPConnection authConnection, OAStanza stanza, long replyTimeout) {
        StanzaCollector collector = authConnection
                .createStanzaCollector(new EmptyIncrementedIdReplyFilter(stanza, authConnection));
        messageLock.lock();
        try {
            authConnection.sendStanza(stanza);
            return getNextStanzaSkipContinues(collector, replyTimeout, authConnection);
        } catch (InterruptedException | SmackException | XMPPErrorException e) {
            throw new RuntimeException("Failed communicating with Harmony Hub", e);
        } finally {
            messageLock.unlock();
            collector.cancel();
        }
    }

    private <R extends OAStanza> R sendOAStanza(XMPPTCPConnection authConnection, OAStanza stanza,
            Class<R> replyClass) {
        return sendOAStanza(authConnection, stanza, replyClass, DEFAULT_REPLY_TIMEOUT);
    }

    private <R extends OAStanza> R sendOAStanza(XMPPTCPConnection authConnection, OAStanza stanza, Class<R> replyClass,
            long replyTimeout) {
        StanzaCollector collector = authConnection.createStanzaCollector(new OAReplyFilter(stanza, authConnection));
        messageLock.lock();
        try {
            authConnection.sendStanza(stanza);
            return replyClass.cast(getNextStanzaSkipContinues(collector, replyTimeout, authConnection));
        } catch (InterruptedException | SmackException | XMPPErrorException e) {
            throw new RuntimeException("Failed communicating with Harmony Hub", e);
        } finally {
            messageLock.unlock();
            collector.cancel();
        }
    }

  
  private Stanza getNextStanzaSkipContinues(StanzaCollector collector, long replyTimeout,
                                            XMPPTCPConnection authConnection) throws InterruptedException, NoResponseException, XMPPErrorException {
    while (true) {
      Stanza reply = collector.nextResult(replyTimeout);
      if (reply == null) {
        throw NoResponseException.newWith(authConnection, collector);
      }
      if (reply instanceof OAStanza && ((OAStanza) reply).isContinuePacket()) {
        continue;
      }
      return reply;
    }
  }

}
