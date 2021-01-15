package com.gnetlab.callkit;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.Manifest;
import android.telecom.Connection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.util.Log;

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
    private String appName;
    private String from;
    private String to;
    private String realCallTo;
    private static HashMap<String, ArrayList<CallbackContext>> callbackContextMap = new HashMap<String, ArrayList<CallbackContext>>();
    private static CordovaInterface cordovaInterface;
    private static CordovaWebView cordovaWebView;
    private static Icon icon;
    private static CordovaCall instance;
    private boolean isAudioFocused = false;
    private int savedAudioMode;

    public static HashMap<String, ArrayList<CallbackContext>> getCallbackContexts() {
        return callbackContextMap;
    }

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
        callbackContextMap.put("answer", new ArrayList<CallbackContext>());
        callbackContextMap.put("reject", new ArrayList<CallbackContext>());
        callbackContextMap.put("hangup", new ArrayList<CallbackContext>());
        callbackContextMap.put("sendCall", new ArrayList<CallbackContext>());
        callbackContextMap.put("receiveCall", new ArrayList<CallbackContext>());

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
        if (action.equals("receiveCall")) {
            Connection conn = MyConnectionService.getConnection();
            if (conn != null) {
                if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("You can't receive a call right now");
                }
            } else {
                from = args.getString(0);
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
                to = args.getString(0);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
                /*cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getCallPhonePermission();
                    }
                });*/
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
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                conn.setDisconnected(cause);
                conn.destroy();
                MyConnectionService.deinitConnection();
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext cbContext : callbackContexts) {
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            cbContext.sendPluginResult(result);
                        }
                    });
                }
                this.callbackContext.success("Call ended successfully");
            }
            return true;
        } else if (action.equals("registerEvent")) {
            String eventType = args.getString(0);
            ArrayList<CallbackContext> callbackContextList = callbackContextMap.get(eventType);
            callbackContextList.add(this.callbackContext);
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
        } else if (action.equals("setAudioMode")) {
            String mode = args.getString(0);
            this.callbackContext.success(this.setAudioMode(mode).toString());
            return true;
        } else if (action.equals("getAudioMode")) {
            this.callbackContext.success(this.getAudioMode());
            return true;
        } else if (action.equals("getAudioModes")) {
            this.callbackContext.success(this.getAudioModes());
            return true;
        }
        return false;
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
        callInfo.putString("from", from);
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    private void sendCall() {
        Uri uri = Uri.fromParts("tel", to, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("to", to);
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        tm.placeCall(uri, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
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

    /**
     * New API
     **/

    private JSONObject getAudioModes() {
        final Context context = this.cordova.getActivity().getApplicationContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        try {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

            JSONArray retdevs = new JSONArray();
            for (AudioDeviceInfo dev : devices) {
                if (dev.isSink()) {
                    int deviceType = dev.getType();
                    boolean addDevice = true;
                    String deviceTypeName = "";

                    switch (deviceType) {
                        case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                            deviceTypeName = "earpiece";
                            break;
                        case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                            deviceTypeName = "bluetooth";
                            break;
                        case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                            deviceTypeName = "speaker";
                            break;
                        default:
                            addDevice = false;
                            break;
                    }

                    if (addDevice) {
                        retdevs.put(
                                new JSONObject()
                                        .put("id", dev.getId())
                                        .put("type", dev.getType())
                                        .put("mode", deviceTypeName)
                                        .put("name", dev.getProductName().toString())
                        );
                    }
                }
            }

            return new JSONObject().put("devices", retdevs);
        } catch (JSONException e) {
            // lets hope json-object keys are not null and not duplicated :)
        }

        return new JSONObject();
    }

    private String getAudioMode() {
        final Context context = this.cordova.getActivity().getApplicationContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int mode = audioManager.getMode();
        boolean isBluetoothScoOn = audioManager.isBluetoothScoOn();
        boolean isSpeakerphoneOn = audioManager.isSpeakerphoneOn();

        Log.w(TAG, "getAudioMode: mode = " + mode + ", bluetooth: " + isBluetoothScoOn + ", speaker: " + isSpeakerphoneOn);

        if (mode == AudioManager.MODE_IN_COMMUNICATION && isBluetoothScoOn) {
            return "bluetooth";
        } else if (mode == AudioManager.MODE_IN_COMMUNICATION && !isBluetoothScoOn && !isSpeakerphoneOn) {
            return "earpiece";
        } else if (mode == AudioManager.MODE_IN_COMMUNICATION && !isBluetoothScoOn && isSpeakerphoneOn) {
            return "speaker";
        } else if (mode == AudioManager.MODE_RINGTONE && !isSpeakerphoneOn) {
            return "ringtone";
        } else if (mode == AudioManager.MODE_NORMAL && !isSpeakerphoneOn) {
            return "normal";
        }

        return "normal";
    }

    private Boolean setAudioMode(String mode) {
        Log.w(TAG, "SetAudioMode:" + getAudioMode() + " -> " + mode);

        if (mode.equals("bluetooth")) {
            return this.bluetoothOn();
        } else if (mode.equals("earpiece")) {
            return this.earpieceOn();
        } else if (mode.equals("speaker")) {
            return this.speakerOn();
        } else if (mode.equals("normal")) {
            return this.normalOn();
        } else {
            Log.w(TAG, "SetAudioMode: invalid mode");
        }

        return false;
    }

    /** Private NEW API */

    private boolean earpieceOn() {
        Connection conn = MyConnectionService.getConnection();
        if (conn != null) {
            conn.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
        } else {
            final Context context = this.cordova.getActivity().getApplicationContext();
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            requestAudioFocus();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
            audioManager.setSpeakerphoneOn(false);
        }

        Log.w(TAG, "SetAudioMode: result = " + getAudioMode());
        return true;
    }

    private boolean bluetoothOn() {
        Connection conn = MyConnectionService.getConnection();
        if (conn != null) {
            conn.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
        } else {
            final Context context = this.cordova.getActivity().getApplicationContext();
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            requestAudioFocus();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
        }

        Log.w(TAG, "SetAudioMode: result = " + getAudioMode());
        return true;
    }

    private boolean speakerOn() {
        Connection conn = MyConnectionService.getConnection();
        if (conn != null) {
            conn.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        } else {
            final Context context = this.cordova.getActivity().getApplicationContext();
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            requestAudioFocus();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
            audioManager.setSpeakerphoneOn(true);
        }

        Log.w(TAG, "SetAudioMode: result = " + getAudioMode());
        return true;
    }

    private boolean normalOn() {
        Connection conn = MyConnectionService.getConnection();
        if (conn != null) {
            conn.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        } else {
            final Context context = this.cordova.getActivity().getApplicationContext();
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            requestAudioFocus();
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
            releaseAudioFocus();
        }

        Log.w(TAG, "SetAudioMode: result = " + getAudioMode());
        return true;
    }

    private void requestAudioFocus() {
        if (isAudioFocused) return;

        final Context context = this.cordova.getActivity().getApplicationContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        AudioManager.OnAudioFocusChangeListener afChangeListener =
                new AudioManager.OnAudioFocusChangeListener() {
                    public void onAudioFocusChange(int focusChange) {
                        String focusChangeStr;
                        switch (focusChange) {
                            case AudioManager.AUDIOFOCUS_GAIN:
                                focusChangeStr = "AUDIOFOCUS_GAIN";
                                break;
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT";
                                break;
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
                                break;
                            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                                focusChangeStr = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS:
                                CordovaCall.this.isAudioFocused = false;
                                focusChangeStr = "AUDIOFOCUS_LOSS";
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                CordovaCall.this.isAudioFocused = false;
                                focusChangeStr = "AUDIOFOCUS_LOSS_TRANSIENT";
                                break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                CordovaCall.this.isAudioFocused = false;
                                focusChangeStr = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
                                break;
                            default:
                                focusChangeStr = "AUDIOFOCUS_UNKNOW";
                                break;
                        }

                        Log.w(TAG, "onAudioFocusChange: " + focusChange + " - " + focusChangeStr);
                    }
                };

        int result;

        // save previous mode
        savedAudioMode = audioManager.getMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "Request Audio Focus: Android >= O");
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(false)
                            .setOnAudioFocusChangeListener(afChangeListener)
                            .build();
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            Log.w(TAG, "Request Audio Focus: Android < O");
            result = audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            isAudioFocused = true;
            Log.w(TAG, "AudioFocus granted");

            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            Log.w(TAG, "AudioManager set mode to MODE_IN_COMMUNICATION");
        } else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            isAudioFocused = false;
            Log.w(TAG, "AudioFocus failed");
        }
    }

    private void releaseAudioFocus() {
        if (!isAudioFocused) return;

        final Context context = this.cordova.getActivity().getApplicationContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        audioManager.setMode(savedAudioMode);
        audioManager.abandonAudioFocus(null);
        isAudioFocused = false;
    }
}
