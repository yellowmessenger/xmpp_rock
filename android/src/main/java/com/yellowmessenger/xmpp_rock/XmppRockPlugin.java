package com.yellowmessenger.xmpp_rock;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import org.jxmpp.stringprep.XmppStringprepException;

import io.flutter.Log;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * XmppRockPlugin
 */
public class XmppRockPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
    public static final String TAG = "eventchannel";
    public static final String STREAM = "com.yellowmessenger.xmpp/stream";
    private static final String CHANNEL = "com.yellowmessenger.xmpp/methods";
    private EventChannel.EventSink mEventSink;
    private Registrar mRegistrar;
    private Context mContext;
    private MethodChannel methodChannel;
    private EventChannel eventChannel;



    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        onAttachedToEngine(flutterPluginBinding.getApplicationContext(), flutterPluginBinding.getFlutterEngine().getDartExecutor());
    }

    private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        this.mContext = applicationContext;
        methodChannel = new MethodChannel(messenger, CHANNEL);
        eventChannel = new EventChannel(messenger, STREAM);
        eventChannel.setStreamHandler(this);
        methodChannel.setMethodCallHandler(this);
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "xmpp_rock");
        channel.setMethodCallHandler(new XmppRockPlugin());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("initializeXMPP")) {
            String fullJid = call.argument("fullJid");
            String xmppPassword = call.argument("password");
            int port = call.argument("port");
            result.success(initXMPP(mContext, fullJid, xmppPassword, port));

        } else  if (call.method.equals("closeConnection")) {
            android.util.Log.d(TAG, "called from  Flutter method channel");

            result.success(closeConnetion());

        }

        else  if (call.method.equals("start-chatbot")) {
            // result.success(closeConnetion());
        }
        else {

            result.notImplemented();
        }

    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        android.util.Log.d(TAG, "called from  detached engine");

        closeConnetion();
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        mEventSink = events;
        mEventSink.success("Stream Connected");
        try {
           if(! MyBus.getInstance().bus().hasObservers())
            MyBus.getInstance().bus().toObservable()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object -> {
                                mEventSink.success(object);
                            }
                    );
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

    }

    @Override
    public void onCancel(Object arguments) {
        android.util.Log.d(TAG, "called from  event channel onCancel");
        closeConnetion();
    }


    private Boolean initXMPP(Context mContext, String fullJid, String xmppPassword, int port) {
        try {
            XmppService xmppServiceInstance;
            xmppServiceInstance = XmppService.getInstance();

            xmppServiceInstance.init(fullJid, xmppPassword, port);
            xmppServiceInstance.connectConnection(mContext);
            return xmppServiceInstance.connection.isConnected();


        } catch (XmppStringprepException e) {
            e.printStackTrace();
            return false;
        }

    }

    private Boolean closeConnetion() {
        try {
            XmppService xmppServiceInstance;
            xmppServiceInstance = XmppService.getInstance();

            xmppServiceInstance.disconnectConnection();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

}
