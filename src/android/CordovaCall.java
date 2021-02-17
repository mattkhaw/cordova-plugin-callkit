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
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

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

        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:] ACTION INCOMING2: START ********'");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:] ACTION INCOMING2: '");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:] ACTION INCOMING2: action:'" + action + "')");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:] ACTION INCOMING2: args:'" + args + "')");
        Log.w(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:] ACTION INCOMING2: '");


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
        } else if (action.equals("receiveCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getLastConnection();
            //--------------------------------------------------------------------------------------
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'receiveCall'] connection[0] is STATE_ACTIVE - You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'receiveCall'] connection[0] is NOT STATE_ACTIVE - You can't receive a call right now - your in a NON ACTIVE call e.g. on hold");
                }
            } else {
                incomingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "receiveCall";
                this.checkCallPermission();
            }
            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("sendCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getLastConnection();
            //--------------------------------------------------------------------------------------
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'sendCall'] connection[0] is STATE_ACTIVE - You can't make a call right now because you're already in a call");

                } else if (conn.getState() == Connection.STATE_DIALING) {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'sendCall'] connection[0]  - You can't make a call right now because you're already trying to make a call");
                } else {
                    this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'sendCall'] You can't make a call right now");
                }
            } else {
                outgoingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
            }
            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("connectCall")) {
            //--------------------------------------------------------------------------------------
            //Connection conn = MyConnectionService.getConnection();

            //for CALL WAITING there can be 2 connections - get [0]
            Connection conn = MyConnectionService.getLastConnection();
            //--------------------------------------------------------------------------------------
            if (conn == null) {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'connectCall'] No call exists for you to connect");

            } else if (conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'connectCall'] Your call is already connected");

            } else {
                conn.setActive();
                Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), this.cordova.getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(intent);
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:] [action:'connectCall'] Call connected successfully");
            }
            return true;
        } else if (action.equals("endCall")) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'endCall'] RECEIVED");


            //--------------------------------------------------------------------------------------
            //endCall - issue
            //--------------------------------------------------------------------------------------
            //CORDOVA send endCall repeatedly till PLUGIN responds with "hangup"
            // This was fine when we had one connection
            // but with CALL WAITING we could have two
            // and the second endCall hangs up the 2nd call as well
            // we fixed it by passing callid to endCall(callId,...)

            //--------------------------------------------------------------------------------------
            //endCall
            //--------------------------------------------------------------------------------------
            Connection conn = null;

            if (null != args) {
                String callId = (String) args.getString(0);

                Log.e(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'endCall'] RECEIVED with callId:" + callId);
                if (null != callId) {
                    conn = MyConnectionService.getConnectionByCallId(callId);

                } else {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'endCall'] callId is null in args - cant call getConnectionByCallId(callId)");
                }
            } else {
                Log.e(TAG, "args is null - for endCall - should be callId");
                //----------------------------------------------------------------------------------
                //should not happen - callId specified in ednCall endCall(callId)
                //----------------------------------------------------------------------------------
                //conn = MyConnectionService.getLastConnection();

            }


            //----------------------------------------------------------------------------------
            if (conn == null) {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'endCall'] getConnectionByCallId(callId) NOT FOUND (endCall can be sent multiple times the 1st one gets hangup response IGNORE the rest) ");
            } else {
                conn.onDisconnect();
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'endCall'] Call ended successfully");
            }
            //----------------------------------------------------------------------------------
            return true;
        } else if (action.equals("setAppName")) {
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
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'setAppName'] App Name Changed Successfully");
            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("setIcon")) {
            //--------------------------------------------------------------------------------------
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName, "drawable", this.cordova.getActivity().getPackageName());
            if (iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'setIcon'] Icon Changed Successfully");
            } else {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'setIcon'] This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
            }

            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("mute")) {

            this.mute();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'mute'] Muted Successfully");
            return true;

        } else if (action.equals("unmute")) {
            this.unmute();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'unmute'] Unmuted Successfully");
            return true;

        } else if (action.equals("speakerOn")) {
            this.speakerOn();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'speakerOn'] Speakerphone is on");
            return true;

        } else if (action.equals("speakerOff")) {
            this.speakerOff();
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'speakerOff'] Speakerphone is off");
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
                this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'callNumber'] Call Successful");
            } else {
                this.callbackContext.error("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'callNumber'] Call Failed. You need to enter a phone number.");

            }
            return true;
            //--------------------------------------------------------------------------------------
        } else if (action.equals("reportCallEndedReason")) {
            this.callbackContext.success("[VOIPCALLKITPLUGIN][CordovaCall][execute:][action:'reportCallEndedReason'] do nothing");
            return true;
        }

        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cordovaWebView = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void processCallNotification(Context context, String notificationType, String notificationData) {

        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] START ******** ");
        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] " + notificationType);

        //------------------------------------------------------------------------------------------
        //LATEST CONNECTION
        //------------------------------------------------------------------------------------------
        //in new call - latest is the new call

        //in multiple call (Incoming call while you are on a call)
        //latest is the incoming Call
        //latest set in addConnection
        //------------------------------------------------------------------------------------------
        //connection size can be 0,1,2
        //if 0 - nothing to drop
        //if 1 remove 1 - user ending call
        //if 2 remove 1 - Incoming call - end first

        String notification_callId = null;
        try {
            JSONObject json = new JSONObject(notificationData);
            if(null != json){
                notification_callId = json.getString("callId");
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] " + notificationType + " notification_callId:" + notification_callId);
            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] " + notificationType + " notification_callId: json is null");

            }

        } catch (JSONException e) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] couldnt parse JSON for notification_callId");

        }



        //------------------------------------------------------------------------------------------
        //Find connection from callId in notification JSON
        //------------------------------------------------------------------------------------------
        //I was getting ghost notification whilst debugging
        //I had kill the app whilst in the middle of a call dialling on the web
        //I disconnected on web
        //restarted the app
        //but nothing in connections dictionary
        //so use the callId in the notification - dont presume anything in the app yet
        Connection notification_Connection = null;
        
        if(null != notification_callId){
            notification_Connection = MyConnectionService.getConnectionByCallId(notification_callId);
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] " + notificationType + " notification_Connection: FOUND");
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] " + notificationType + " notification_Connection: NOT FOUND (notification_callId)");
        }


        switch (notificationType) {
            case "CallCreated":
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallCreated >> CALL registerIncomingCall]");
                registerIncomingCall(context, notificationData);
                break;
            case "CallAnsweredElsewhere":
                if (null != notification_Connection) {
                    if (!MyConnectionService.isActive(notification_Connection)) {

                        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallAnsweredElsewhere - CALL dropConnection [DisconnectCause(.ANSWERED_ELSEWHERE)]");

                        MyConnectionService.dropConnection(notification_Connection, new DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE));

                    }else {
                        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallAnsweredElsewhere + isActive - SKIP dropConnection");
                    }
                } else {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallAnsweredElsewhere - notification_Connection is null");
                }
                break;
                
            case "CallDeclinedElsewhere":

                if (null != notification_Connection) {
                    if (!MyConnectionService.isActive(notification_Connection)) {
                        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallDeclinedElsewhere - CALL dropConnection [DisconnectCause(.REJECTED)]");
                        MyConnectionService.dropConnection(notification_Connection, new DisconnectCause(DisconnectCause.REJECTED));
                    }else {
                        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallDeclinedElsewhere + isActive - SKIP dropConnection");
                    }
                } else {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallDeclinedElsewhere - notification_Connection is null");
                }

                break;
                
            case "CallCompleted":

                if (null != notification_Connection) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallCompleted - CALL dropConnection [DisconnectCause(.REMOTE)]");
                    MyConnectionService.dropConnection(notification_Connection, new DisconnectCause(DisconnectCause.REMOTE));
                } else {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallCompleted - notification_Connection is null - CANT dropConnection - MAY BE OLD CallComplete ");
                }

                break;
                
            case "CallMissed":

                if (null != notification_Connection) {
                    MyConnectionService.dropConnection(notification_Connection, new DisconnectCause(DisconnectCause.MISSED));
                } else {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [processCallNotification:] >>> CallMissed - notification_Connection is null - CANT dropConnection");
                }
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void registerIncomingCall(Context context, String callerDataSerialized) {
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [registerIncomingCall] STARTED");

        //------------------------------------------------------------------------------------------
        if (null != callerDataSerialized) {
            Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [registerIncomingCall:] callerDataSerialized: START ********");
            Log.w(TAG, callerDataSerialized);
            Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [registerIncomingCall:] callerDataSerialized: END **********");

        }
        //------------------------------------------------------------------------------------------
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
        } else {
            Log.e(TAG, "checkCallPermission: permissionCounter is NOT >= 1:" + permissionCounter);
        }
        permissionCounter--;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void receiveCall() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall receiveCall() STARTED");

        Bundle callInfo = new Bundle();
        callInfo.putString("incomingCall", incomingCall.toString());
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void sendCall() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall sendCall() STARTED");
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

        //Android Studio said add this check before placeCall
        //never tested we dont send calls
        if (ActivityCompat.checkSelfPermission(this.cordova.getActivity().getApplicationContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            tm.placeCall(uri, callInfo);
        }

        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall mute() CALLED" );
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall unmute() CALLED" );
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
        Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall callNumber() CALLED" );
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
                Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall onRequestPermissionResult >> CALL_PHONE_REQ_CODE");
                this.sendCall();
                break;

            case REAL_PHONE_CALL:
                Log.e(TAG, "[VOIPCALLKITPLUGIN] CordovaCall onRequestPermissionResult >> REAL_PHONE_CALL");
                this.callNumber();
                break;
        }
    }

    public static void sendJsonResult(String eventName, JSONObject json) {
        //loged below as well Log.d(TAG, "[VOIPCALLKITPLUGIN] CordovaCall sendJsonResult(eventName:'" + eventName + "' json:" + json.toString() );

        if (cordovaWebView != null) {
            sendJson(eventName, json);
            return;
        }

        //Log.d(TAG, "[VOIPCALLKITPLUGIN] CordovaCall sendJsonResult caching event data " + eventName);
        cachedEvents.put(eventName, json);
    }

    //PLUGIN sends messages abck to CORDOVA to trigger next action
    // e.g. user press ANSWER button
    // PLUGIN returns 'answer' to CORDOVA
    // CORDOVA triggers JS answerCall()
    // others IN:'endCall' end voip connection and UI > OUT: 'hangup'

    private static void sendJson(String eventName, JSONObject json) {
        //--------------------------------------------------------------------------------------

        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [sendJSON] RETURN RESPONSE TO CORDOVA - START *************************" );
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [sendJSON] <<<< JSMESSAGE OUT - eventName:'" + eventName );
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [sendJSON] <<<< JSMESSAGE OUT - json:");
        //--------------------------------------------------------------------------------------
        Log.w(TAG, json.toString().substring(20));
        //--------------------------------------------------------------------------------------

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

        Log.e(TAG, "[VOIPCALLKITPLUGIN] [CordovaCall] [sendJSON] RESPONSE TO CORDOVA - END   *************************" );

    }
}
