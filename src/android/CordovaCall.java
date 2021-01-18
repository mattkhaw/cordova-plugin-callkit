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
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
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

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        this.checkCallPermission();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (action.equals("init")) {
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
        } else if (action.equals("receiveCall")) {
            Connection conn = MyConnectionService.getConnection();
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("You can't receive a call right now");
                }
            } else {
                incomingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "receiveCall";
                this.checkCallPermission();
            }
            return true;
        } else if (action.equals("sendCall")) {
            Connection conn = MyConnectionService.getConnection();
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't make a call right now because you're already in a call");
                } else if (conn.getState() == Connection.STATE_DIALING) {
                    this.callbackContext.error("You can't make a call right now because you're already trying to make a call");
                } else {
                    this.callbackContext.error("You can't make a call right now");
                }
            } else {
                outgoingCall = args.optJSONObject(0);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
            }
            return true;
        } else if (action.equals("connectCall")) {
            Connection conn = MyConnectionService.getConnection();
            if (conn == null) {
                this.callbackContext.error("No call exists for you to connect");
            } else if (conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("Your call is already connected");
            } else {
                conn.setActive();
                Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), this.cordova.getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(intent);
                this.callbackContext.success("Call connected successfully");
            }
            return true;
        } else if (action.equals("endCall")) {
            Connection conn = MyConnectionService.getConnection();
            if (conn == null) {
                this.callbackContext.error("No call exists for you to end");
            } else {
                conn.onDisconnect();
                this.callbackContext.success("Call ended successfully");
            }
            return true;
        } else if (action.equals("setAppName")) {
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
            this.callbackContext.success("App Name Changed Successfully");
            return true;
        } else if (action.equals("setIcon")) {
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName, "drawable", this.cordova.getActivity().getPackageName());
            if (iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("Icon Changed Successfully");
            } else {
                this.callbackContext.error("This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
            }
            return true;
        } else if (action.equals("mute")) {
            this.mute();
            this.callbackContext.success("Muted Successfully");
            return true;
        } else if (action.equals("unmute")) {
            this.unmute();
            this.callbackContext.success("Unmuted Successfully");
            return true;
        } else if (action.equals("speakerOn")) {
            this.speakerOn();
            this.callbackContext.success("Speakerphone is on");
            return true;
        } else if (action.equals("speakerOff")) {
            this.speakerOff();
            this.callbackContext.success("Speakerphone is off");
            return true;
        } else if (action.equals("callNumber")) {
            realCallTo = args.getString(0);
            if (realCallTo != null) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        callNumberPhonePermission();
                    }
                });
                this.callbackContext.success("Call Successful");
            } else {
                this.callbackContext.error("Call Failed. You need to enter a phone number.");
            }
            return true;
        }

        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cordovaWebView = null;
    }

    public static void registerIncomingCall(Context context, String callerDataSerialized) {
        TelecomManager tm = (TelecomManager) context.getApplicationContext().getSystemService(context.getApplicationContext().TELECOM_SERVICE);
        String appName = getApplicationName(context);
        PhoneAccountHandle handle = new PhoneAccountHandle(new ComponentName(context.getApplicationContext(), MyConnectionService.class), appName);
        Bundle callInfo = new Bundle();
        callInfo.putString("incomingCall", callerDataSerialized);
        tm.addNewIncomingCall(handle, callInfo);
    }

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
        }
        permissionCounter--;
    }

    private void receiveCall() {
        Bundle callInfo = new Bundle();
        callInfo.putString("incomingCall", incomingCall.toString());
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    private void sendCall() {
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
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
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
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", realCallTo, null));
            this.cordova.getActivity().getApplicationContext().startActivity(intent);
        } catch (Exception e) {
            this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

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
                this.sendCall();
                break;
            case REAL_PHONE_CALL:
                this.callNumber();
                break;
        }
    }

    public static void sendJsonResult(String eventName, JSONObject json) {
        if (cordovaWebView != null) {
            Log.d(TAG, "sending json directly " + eventName);
            sendJson(eventName, json);
            return;
        }

        Log.d(TAG, "caching event data " + eventName);
        cachedEvents.put(eventName, json);
    }

    private static void sendJson(String eventName, JSONObject json) {
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
