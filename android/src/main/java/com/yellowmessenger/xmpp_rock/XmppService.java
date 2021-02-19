package com.yellowmessenger.xmpp_rock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.ping.packet.Ping;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.util.JidUtil;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import de.measite.minidns.DNSClient;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;

import static org.jivesoftware.smack.packet.Presence.Type.available;


public class XmppService {

    private String DOMAIN = "";
    private String HOST = "";
    private int PORT = 5222;
    private String JID = "";
    private String userName = "";
    private String passWord = "";
    AbstractXMPPConnection connection;
    ChatManager chatmanager;
    Chat newChat;
    XMPPConnectionListener connectionListener = new XMPPConnectionListener();
    private boolean connected;
    private boolean isToasted;
    private boolean chat_created;
    private boolean loggedin;
    private  String resource = "";

    StanzaFilter packetFilter = new StanzaTypeFilter(Message.class);

    private static XmppService xmppServiceInstance;

    private XmppService(){}

    public static XmppService getInstance(){
        if(xmppServiceInstance == null){
            xmppServiceInstance = new XmppService();
        }
        return xmppServiceInstance;
    }



    //Initialize
    public void init(String jid, String pwd, int port)  throws XmppStringprepException {
        Log.i("XMPP", "Initializing!");


        this.JID = jid;
        this.userName = XmppStringUtils.parseLocalpart(JID);
        this.DOMAIN = XmppStringUtils.parseDomain(JID);
        this.passWord = pwd;
        this.PORT = port;
        this.HOST = DOMAIN;
        this.resource = XmppStringUtils.parseResource(JID);


        XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder();
        configBuilder.setUsernameAndPassword(userName, passWord);
        configBuilder.setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);
        configBuilder.setXmppDomain(DOMAIN);
        configBuilder.setHost(HOST);
        configBuilder.setPort(PORT);
        configBuilder.setResource(resource);
        configBuilder.setSendPresence(true);
        configBuilder.setConnectTimeout(10000);
        configBuilder.setSocketFactory(SSLSocketFactory.getDefault());

//        configBuilder.setDebuggerEnabled(true);
        try {
            configBuilder.setCustomSSLContext(SSLContext.getInstance("TLS"));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (connection == null) {

            connection = new XMPPTCPConnection(configBuilder.build());
            connection.setPacketReplyTimeout(5000);

            connection.addSyncStanzaListener(new StanzaListener() {
                @Override
                public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException {

                    if (packet instanceof Message) {
                        Message message = (Message) packet;
                        Jid sender = message.getFrom();
                        if(!message.getBodies().isEmpty() && message.getBodies().toArray().length > 0){
                            Log.d("Received message:", " "
                                    + (((Message.Body)(message.getBodies().toArray()[0])).getMessage()  != null ? ((Message.Body)(message.getBodies().toArray()[0])).getMessage() : "NULL"));

                            MyBus.getInstance().bus().send((((Message.Body)(message.getBodies().toArray()[0])).getMessage() != null ?((Message.Body)(message.getBodies().toArray()[0])).getMessage() : "{\"data\": null}"));

                        }
                    }
                }
            }, packetFilter);
            connection.addConnectionListener(connectionListener);

        }
//        else{
//
//        }


    }

    // Disconnect Function
    public void disconnectConnection() {


        try {
            HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
            handlerThread.start();
            Looper looper = handlerThread.getLooper();
            Handler handler = new Handler(looper);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (connection != null && connection.isConnected()) {
                        connection.disconnect();
                        connection = null;
                    }
                }
            });
        }
        catch (Exception e){
            Log.e("Disconnection error",e.getMessage());
        }

//            new Thread(() -> {
//                connection.disconnect();
//                connection = null;
//            }).start();
//        }


    }


    @SuppressLint("StaticFieldLeak")
    public void connectConnection(Context mContext) {
        if(!connected) {

            DNSClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
            DNSClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
            DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(mContext));
            AsyncTask<Void, Void, Boolean> connectionThread;
            connectionThread = new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected void onCancelled() {
                    super.onCancelled();
                    MyBus.getInstance().bus().send("{\"connected\": "+ false + "}");
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    super.onPostExecute(aBoolean);
//                MyBus.getInstance().bus().send("{\"connected\": "+ aBoolean.toString() + "}");
                }



                @Override
                protected Boolean doInBackground(Void... arg0) {
                    // Create a connection
                    try {
                        if(connection != null) {
                            if (connection.isConnected()) {
                                connection.disconnect();
                            }
                            connection.connect();
//                            login();
                            connected = true;
                        }
//                        Presence p = new Presence(available, "AVAILABLE", 1, Presence.Mode.available);
//                        try {
//                            connection.sendStanza(p);
//                        } catch (SmackException.NotConnectedException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (SmackException e) {
                        e.printStackTrace();
                    } catch (XMPPException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return connected;
                }


            };
            connectionThread.execute();

        }

    }

//
//    public void sendMsg(String jid, String message) {
//        if (connection.isConnected() == true) {
//            // Assume we've created an XMPPConnection name "connection"._
//            chatmanager = ChatManager.getInstanceFor(connection);
//            try {
//                newChat = chatmanager.chatWith(JidCreate.from(jid).asEntityBareJidIfPossible());
//            } catch (XmppStringprepException e) {
//                e.printStackTrace();
//            }
//            try {
//                newChat.send(message);
//            } catch (SmackException.NotConnectedException e) {
//                e.printStackTrace();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }


    public void login() {
        if(connection != null && connection.isConnected() && !connection.isAuthenticated()){

            try {
                connection.login(userName, passWord);
            } catch (XMPPException e) {
                e.printStackTrace();
            } catch (SmackException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

//            HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
//            handlerThread.start();
//            Looper looper = handlerThread.getLooper();
//            Handler handler = new Handler(looper);
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        connection.login(userName, passWord);
//                    } catch (XMPPException e) {
//                        e.printStackTrace();
//                    } catch (SmackException e) {
//                        e.printStackTrace();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });

        }
    }


    //Connection Listener to check connection state
    public class XMPPConnectionListener implements ConnectionListener {

        @Override
        public void connected(final XMPPConnection connection) {
            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub

                    }
                });
            Log.d("xmpp", "Connected!");
            MyBus.getInstance().bus().send("{\"connected\": "+ true + "}");
            if (!connection.isAuthenticated()) {
                login();
            }
            connected = true;

        }

        @Override
        public void connectionClosed() {
            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                    }
                });
            Log.d("xmpp", "ConnectionCLosed!");
            MyBus.getInstance().bus().send("{\"connected\": "+ false + "}");
            connected = false;
            chat_created = false;
            loggedin = false;
        }


        @Override
        public void connectionClosedOnError(Exception arg0) {
            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                    }
                });

            Log.d("xmpp", "ConnectionClosedOn Error!");
            MyBus.getInstance().bus().send("{\"connected\": "+ false + "}");
            connected = false;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void reconnectingIn(int arg0) {
            Log.d("xmpp", "Reconnectingin " + arg0);
            loggedin = false;
        }

        @Override
        public void reconnectionFailed(Exception arg0) {
            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
            Log.d("xmpp", "ReconnectionFailed!");
            connected = false;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void reconnectionSuccessful() {
            Log.d("xmpp", "ReAuthenticated!");

            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                    }
                });

            Log.d("xmpp", "ReconnectionSuccessful");
            connected = true;
            chat_created = false;
            loggedin = false;
        }

        @Override
        public void authenticated(XMPPConnection arg0, boolean arg1) {

            if(connection.isAuthenticated())
            {
                Log.d("xmpp", "Authenticated!");
                HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
                handlerThread.start();
                Looper looper = handlerThread.getLooper();
                Handler handler = new Handler(looper);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (connection != null) {
                            Presence p = new Presence(available, "AVAILABLE", 1, Presence.Mode.available);
                            try {
                                connection.sendStanza(p);
                            } catch (SmackException.NotConnectedException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                connected = true;
                loggedin = true;
                chat_created = false;
                MyBus.getInstance().bus().send("{\"authenticated\": "+ connection.isAuthenticated() + "}");
            }
            if (isToasted)
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                    }
                });
        }
    }
}

