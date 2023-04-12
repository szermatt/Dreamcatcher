package net.gmx.szermatt.dreamcatcher.harmony;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Bind;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.concurrent.locks.ReentrantLock;

public class PowerOffTask  {

  public static final int DEFAULT_REPLY_TIMEOUT = 5000; // 30_000;
  public static final int START_ACTIVITY_REPLY_TIMEOUT = 30_000;
  private static final int DEFAULT_PORT = 5222;
  private static final String DEFAULT_XMPP_USER = "guest@connect.logitech.com/gatorade.";
  private static final String DEFAULT_XMPP_PASSWORD = "gatorade.";

  /**
   * To prevent timeouts when different threads send a message and expect a response, create a lock that only allows a
   * single thread at a time to perform a send/receive action.
   */
  private ReentrantLock messageLock = new ReentrantLock();

  
  public void connect(String host) throws Exception {
    // only the 1st time:
    //org.jivesoftware.smack.android.AndroidSmackInitializer.initialize(mContext);
    ProviderManager.addIQProvider(Bind.ELEMENT, Bind.NAMESPACE, new HarmonyBindIQProvider());
    ProviderManager.addIQProvider("oa", "connect.logitech.com", new OAReplyProvider());
    
    XMPPTCPConnectionConfiguration connectionConfig = XMPPTCPConnectionConfiguration.builder()
        .setHost(host)
        .setPort(DEFAULT_PORT)
        .setXmppDomain(host)
        .addEnabledSaslMechanism(SASLMechanism.PLAIN)
        .build();
    HarmonyXMPPTCPConnection authConnection = new HarmonyXMPPTCPConnection(connectionConfig);
    //addPacketLogging(authConnection, "auth");

    System.out.println("connect...");
    authConnection.connect();
    System.out.println("connected.");
    System.out.println("login...");
    authConnection.login(DEFAULT_XMPP_USER, DEFAULT_XMPP_PASSWORD, Resourcepart.from("auth"));
    System.out.println("logged in.");
    authConnection.setFromMode(XMPPConnection.FromMode.USER);

    System.out.println("auth...");
    MessageAuth.AuthRequest sessionRequest = new MessageAuth.AuthRequest();
    MessageAuth.AuthReply oaResponse = sendOAStanza(authConnection, sessionRequest, MessageAuth.AuthReply.class);

    System.out.println("auth ok: " + oaResponse.getUsername() + " " + oaResponse.getPassword());
    System.out.println("disconnect...");
    authConnection.disconnect();
    System.out.println("disconnected.");

    System.out.println("reconnect...");
    HarmonyXMPPTCPConnection connection = new HarmonyXMPPTCPConnection(connectionConfig);
    // addPacketLogging(connection, "main");
    connection.connect();
    System.out.println("reconnected.");
    System.out.println("re-auth...");
    connection.login(oaResponse.getUsername(), oaResponse.getPassword(), Resourcepart.from("main"));
    System.out.println("re-auth ok.");
    connection.setFromMode(XMPPConnection.FromMode.USER);
    System.out.println("power off...");
    sendOAStanza(connection, new MessageStartActivity.StartActivityRequest(-1), MessageStartActivity.StartActivityReply.class, START_ACTIVITY_REPLY_TIMEOUT);
    System.out.println("power off...done");

    System.out.println("disconnect...");
    connection.disconnect();
    System.out.println("disconnected.");

      // heartbeat = scheduler.scheduleAtFixedRate(new Runnable() {
      //     @Override
      //     public void run() {
      //       try {
      //         if (connection.isConnected()) {
      //           sendPing();
      //         }
      //       } catch (Exception e) {
      //         logger.warn("Send heartbeat failed", e);
      //       }
      //     }
      //   }, 30, 30, TimeUnit.SECONDS);

      // monitorActivityChanges();
      // getCurrentActivity();

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
