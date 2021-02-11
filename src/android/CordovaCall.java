package com.dmarc.cordovacall;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.twiliovideo.TwilioVideoActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CordovaCall extends CordovaPlugin {

    private static String TAG = "CordovaCall";
    public static final int CALL_PHONE_REQ_CODE = 0;
    public static final int REAL_PHONE_CALL = 1;
    private int permissionCounter = 0;
    private String pendingAction;
    private TelecomManager tm;
    private PhoneAccountHandle handle;
    private PhoneAccount phoneAccount;
    private CallbackContext callbackContext;
    private static CallbackContext eventCallbackContext;
    private String appName;
    private JSONObject incomingCall;
    private JSONObject outgoingCall;
    private String realCallTo;
    private static CordovaInterface cordovaInterface;
    private static CordovaWebView cordovaWebView;
    private static Icon icon;
    private static CordovaCall instance;
    private static Map<String, JSONObject> cachedEvents = Collections.synchronizedMap(new HashMap<String, JSONObject>());

    private static boolean endCallInProgress = false;


    public static CordovaInterface getCordova() {
        return cordovaInterface;
    }

    public static CordovaWebView getWebView() {
        return cordovaWebView;
    }

    public static Icon getIcon() {
        return icon;
    }

    public static CordovaCall getInstance() {
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        cordovaInterface = cordova;
        cordovaWebView = webView;
        super.initialize(cordova, webView);
        appName = getApplicationName(this.cordova.getActivity().getApplicationContext());
        handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(), MyConnectionService.class), appName);
        tm = (TelecomManager) this.cordova.getActivity().getApplicationContext().getSystemService(this.cordova.getActivity().getApplicationContext().TELECOM_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            phoneAccount = new PhoneAccount.Builder(handle, appName)
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build();
            tm.registerPhoneAccount(phoneAccount);
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            phoneAccount = new PhoneAccount.Builder(handle, appName)
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .build();
            tm.registerPhoneAccount(phoneAccount);
        }

        instance = this;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        this.checkCallPermission();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] ACTION INCOMING2: START ********'");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] ACTION INCOMING2: '");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] ACTION INCOMING2: action:'" + action + "')");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] ACTION INCOMING2: '");


        this.callbackContext = callbackContext;
        if (action.equals("init")) {
            //--------------------------------------------------------------------------------------
            eventCallbackContext = callbackContext;
            if (!cachedEvents.isEmpty()) {
                synchronized (cachedEvents) {
                    for (Map.Entry<String, JSONObject> event : cachedEvents.entrySet()) {
                        sendJson(event.getKey(), event.getValue());
                    }
                }
                cachedEvents.clear();
            }

            return true;
            //--------------------------------------------------------------------------------------
        }
        else if (action.equals("receiveCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getOldestConnection();
            //--------------------------------------------------------------------------------------
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'receiveCall'] connection[0] is STATE_ACTIVE - You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'receiveCall'] connection[0] is NOT STATE_ACTIVE - You can't receive a call right now - your in a NON ACTIVE call e.g. on hold");
                }
            } else {
                incomingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "receiveCall";
                this.checkCallPermission();
            }
            return true;
            //--------------------------------------------------------------------------------------
        }
        else if (action.equals("sendCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getOldestConnection();
            //--------------------------------------------------------------------------------------
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'sendCall'] connection[0] is STATE_ACTIVE - You can't make a call right now because you're already in a call");

                } else if (conn.getState() == Connection.STATE_DIALING) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'sendCall'] connection[0]  - You can't make a call right now because you're already trying to make a call");
                } else {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'sendCall'] You can't make a call right now");
                }
            } else {
                outgoingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
            }
            return true;
            //--------------------------------------------------------------------------------------
        }
        else if (action.equals("connectCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getOldestConnection();
            //--------------------------------------------------------------------------------------
            if (conn == null) {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'connectCall'] No call exists for you to connect");

            } else if (conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'connectCall'] Your call is already connected");

            } else {
                conn.setActive();
                Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), this.cordova.getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(intent);
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:] [action:'connectCall'] Call connected successfully");
            }
            return true;
        }
        else if (action.equals("endCall")) {
            //Log.e(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] RECEIVED" );


            //CORDOVA send endCall repeatedly till PLUGIN responds with "hangup"
            //This was fine when we had one connection
            //but with CALL WAITING we could have two
            // and the second endCall hangs up the 2nd call as well


            if(TwilioVideoActivity.endCall_can_disconnect){

                Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] RECEIVED - TwilioVideoActivity.endCall_can_disconnect is true - DISCONNECT  - FIRST 'endCall' (cordova sends multiple 'endCall' till 'hangup' received )" );

                //set to false endCall can turn up more than once
                Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] RECEIVED - TwilioVideoActivity.endCall_can_disconnect is true > SET TO false to prevent further endCall > conn.onDisconnect();" );

                TwilioVideoActivity.endCall_can_disconnect = false;

                //reset in answerCall - the ist call has ended endCall > closeRoom > answerCall - 2nd on starting

                //--------------------------------------------------------------------------------------
                //Connection conn = MyConnectionService.getConnection();

                //for CALL WAITING there can be 2 connections - get [0]
                Connection conn = MyConnectionService.getOldestConnection();
                //--------------------------------------------------------------------------------------
                if (conn == null) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] No call exists for you to end");
                } else {
                    conn.onDisconnect();
                    this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] Call ended successfully");
                }

                //THE PLUGIN responds with "hangUP" and until it does JS repeatedly sends "endCall"
            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'endCall'] RECEIVED - TwilioVideoActivity.endCall_can_disconnect: false - IGNORE this 'endCall' [BUG:cordova sends multiple 'endCall' till 'hangup' received]" );
            }

            return true;
        }
        else if (action.equals("setAppName")) {
            //--------------------------------------------------------------------------------------
            String appName = args.getString(0);

            handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(), MyConnectionService.class), appName);

            if (android.os.Build.VERSION.SDK_INT >= 26) {
                phoneAccount = new PhoneAccount.Builder(handle, appName)
                        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                        .build();
                tm.registerPhoneAccount(phoneAccount);
            }
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                phoneAccount = new PhoneAccount.Builder(handle, appName)
                        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                        .build();
                tm.registerPhoneAccount(phoneAccount);
            }
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'setAppName'] App Name Changed Successfully");
            return true;
            //--------------------------------------------------------------------------------------
        }
        else if (action.equals("setIcon"))
        {
            //--------------------------------------------------------------------------------------
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName, "drawable", this.cordova.getActivity().getPackageName());
            if (iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'setIcon'] Icon Changed Successfully");
            } else {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'setIcon'] This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
            }

            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("mute")) {

            this.mute();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'mute'] Muted Successfully");
            return true;

        } else if (action.equals("unmute")) {
            this.unmute();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'unmute'] Unmuted Successfully");
            return true;

        } else if (action.equals("speakerOn")) {
            this.speakerOn();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'speakerOn'] Speakerphone is on");
            return true;

        } else if (action.equals("speakerOff")) {
            this.speakerOff();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'speakerOff'] Speakerphone is off");
            return true;

        } else if (action.equals("callNumber")) {
            //--------------------------------------------------------------------------------------
            realCallTo = args.getString(0);

            if (realCallTo != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callNumberPhonePermission();
                    }
                });
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'callNumber'] Call Successful");
            }
            else {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall.java][execute:][action:'callNumber'] Call Failed. You need to enter a phone number.");

            }
            return true;
            //--------------------------------------------------------------------------------------
        }

        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cordovaWebView = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void registerIncomingCall(Context context, String callerDataSerialized) {
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] [registerIncomingCall] STARTED" );
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] [registerIncomingCall:] callerDataSerialized: START ********" );
        Log.w(TAG, callerDataSerialized );
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] [registerIncomingCall:] callerDataSerialized END ********" );

        //NOTE - if user presses ANSWER button its not handled here-  all this the code that handles VOIP CALL UI ANSWER/DECLINE is in MyConnectionService.....onAnswer

        TelecomManager tm = (TelecomManager) context.getApplicationContext().getSystemService(context.getApplicationContext().TELECOM_SERVICE);
        String appName = getApplicationName(context);
        PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName(context.getApplicationContext(), MyConnectionService.class), appName);

        Bundle callInfo = new Bundle();
        callInfo.putString("incomingCall", callerDataSerialized);

        tm.addNewIncomingCall(handle, callInfo);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkCallPermission() {
        if (permissionCounter >= 1) {
            PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
            if (currentPhoneAccount.isEnabled()) {
                if (pendingAction == "receiveCall") {
                    this.receiveCall();
                } else if (pendingAction == "sendCall") {
                    this.sendCall();
                }
            } else {
                if (permissionCounter == 2) {
                    Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                    phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
                } else {
                    this.callbackContext.error("You need to accept phone account permissions in order to send and receive calls");
                }
            }
        }else{
            Log.e(TAG, "checkCallPermission: permissionCounter is NOT >= 1:" + permissionCounter );
        }
        permissionCounter--;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void receiveCall() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java receiveCall() STARTED" );

        Bundle callInfo = new Bundle();
        callInfo.putString("incomingCall", incomingCall.toString());
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void sendCall() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java sendCall() STARTED" );
        String name;
        try {
            name = outgoingCall.getString("callName");
        } catch (JSONException e) {
            Log.e("CORDOVA_CALL", "name does not exist");
            return;
        }
        Uri uri = Uri.fromParts("tel", name, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("outgoingCall", outgoingCall.toString());
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        tm.placeCall(uri, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java mute() CALLED" );
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java unmute() CALLED" );
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
    }

    private void speakerOn() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
    }

    private void speakerOff() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    protected void getCallPhonePermission() {
        cordova.requestPermission(this, CALL_PHONE_REQ_CODE, Manifest.permission.CALL_PHONE);
    }

    protected void callNumberPhonePermission() {
        cordova.requestPermission(this, REAL_PHONE_CALL, Manifest.permission.CALL_PHONE);
    }

    private void callNumber() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java callNumber() CALLED" );
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", realCallTo, null));
            this.cordova.getActivity().getApplicationContext().startActivity(intent);
        } catch (Exception e) {
            this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "CALL_PHONE Permission Denied"));
                return;
            }
        }
        switch (requestCode) {
            case CALL_PHONE_REQ_CODE:
                Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java onRequestPermissionResult >> CALL_PHONE_REQ_CODE");
                this.sendCall();
                break;

            case REAL_PHONE_CALL:
                Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java onRequestPermissionResult >> REAL_PHONE_CALL");
                this.callNumber();
                break;
        }
    }

    public static void sendJsonResult(String eventName, JSONObject json) {
        //loged below as well Log.d(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java sendJsonResult(eventName:'" + eventName + "' json:" + json.toString() );

        if (cordovaWebView != null) {
            sendJson(eventName, json);
            return;
        }

        //Log.d(TAG, "[VOIPCALLKITPLUGIN] CordovaCall.java sendJsonResult caching event data " + eventName);
        cachedEvents.put(eventName, json);
    }

    //PLUGIN sends messages abck to CORDOVA to trigger next action
    // e.g. user press ANSWER button
    // PLUGIN returns 'answer' to CORDOVA
    // CORDOVA triggers JS answerCall()
    // others IN:'endCall' end voip connection and UI > OUT: 'hangup'

    private static void sendJson(String eventName, JSONObject json) {
        //--------------------------------------------------------------------------------------
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] JSMESSAGE OUT > sendJson() - eventName:'" + eventName );
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] JSMESSAGE OUT > sendJson() - json:");
        Log.w(TAG, json.toString().substring(20));
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] JSMESSAGE OUT > sendJson() - json END ***********");

        //--------------------------------------------------------------------------------------
        //RESPONSE TO "endCall" > onDestroy() responds with "hangup"
        //CORDOVA will send endCall over and over till hangup received
        //--------------------------------------------------------------------------------------
        if(eventName.equals("hangup")){
            //--------------------------------------------------------------------------------------
            //DONT DO HERE you can get endCall, endCall,endCall, return hangUp and still get more endCall
            // endCallInProgress = false;
            //--------------------------------------------------------------------------------------
            //Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] JSMESSAGE OUT sendJson() - eventName:'hangup' dont SET endCallInProgress = false here as there may be more endCalls - bug");
            //--------------------------------------------------------------------------------------
        }else if(eventName.equals("answer")){
            //--------------------------------------------------------------------------------------
            //2nd call - user presses ANSWER (on android 10 swipe up)
            //endCallInProgress = false;
            //moved to closeRoom - having it here breaks the very last endCall

            //Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall.java] JSMESSAGE OUT sendJson() - eventName:'answer' SET endCallInProgress = false");
            //--------------------------------------------------------------------------------------
        }else
            {
        	//not hangup - skip
        }

        if (eventCallbackContext == null) {
            return;
        }

        JSONObject total = new JSONObject();
        try {
            total.put("eventName", eventName);
            total.put("data", json);
        } catch (JSONException e) {
            Log.e(TAG, "building event payload failed", e);
            return;
        }


        getCordova().getThreadPool().execute(new Runnable() {
            public void run() {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, total);
                pluginResult.setKeepCallback(true);
                eventCallbackContext.sendPluginResult(pluginResult);
            }
        });
    }
}
