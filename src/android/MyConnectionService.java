package com.dmarc.cordovacall;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.util.Log;

import org.apache.cordova.twiliovideo.TwilioVideoActivity;
import org.json.JSONException;
import org.json.JSONObject;

import live.sea.chat.MainActivity;

public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";
    private static Connection conn;

    public static Connection getConnection() {
        return conn;
    }

    public static void deinitConnection() {
        conn = null;
    }

    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {
        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("incomingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "failed to create incoming connection");
            return null;
        }

        final JSONObject payload = json;
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                Log.e(TAG, "[onCreateIncomingConnection] onAnswer: CALLED");
//                //----------------------------------------------------------------------------------
//                this.setActive();
//
//                //----------------------------------------------------------------------------------
//                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
//
//                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//
//                getApplicationContext().startActivity(intent);
//                //----------------------------------------------------------------------------------
//                CordovaCall.sendJsonResult("answer", payload);
//                //----------------------------------------------------------------------------------


//v2 - call main last
                //----------------------------------------------------------------------------------
                this.setActive();


                //----------------------------------------------------------------------------------
                CordovaCall.sendJsonResult("answer", payload);
                //----------------------------------------------------------------------------------
                //----------------------------------------------------------------------------------
				//TODO  - remove after ANSWER tested on many devices
				//  Intent intent = new Intent(getApplicationContext(), MainActivity.class);
				//
				//  //WRONG TwilioVideoActivity.config can be null
				//  // Intent intent = new Intent(getApplicationContext(), TwilioVideoActivity.class);
				//
				//  intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				//
				//  getApplicationContext().startActivity(intent);

                //------------------------------------------------------------------------------
                //Launch (after delay) so MainActivity + TwilioVideoActivity are ready
                //------------------------------------------------------------------------------
                //CASE 1/2 - app running in foreground/background
                //call > onAnswer > wait 3 secs switch to TwilioVideoActivity


                    //------------------------------------------------------------------------------
                    //switch to TwilioVideoActivity after delay
                    //------------------------------------------------------------------------------
                    Log.e(TAG, "TwilioVideoActivity.isActive() IS TRUE > switch to TwilioVideoActivity after delay");
                    Handler handler;
                    Runnable delayRunnable;

                    handler = new Handler();
                    delayRunnable = new Runnable() {

                        @Override
                        public void run() {
                            //you need to wait before checking isActive TVA.onStart hasnt been called yet
                            //if(TwilioVideoActivity.isActive()){
                            if(TwilioVideoActivity.onResumeHasCompletedAtLeastOnce()){
                                //----------------------------------------------------------------------
                                Log.e(TAG, "DELAY COMPLETE > switch to TwilioVideoActivity");
                                //----------------------------------------------------------------------
                                Intent intent = new Intent(getApplicationContext(), TwilioVideoActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                getApplicationContext().startActivity(intent);
                                //----------------------------------------------------------------------
                            }else{
                                //------------------------------------------------------------------------------
                                //CASE 3 - app not running
                                //call > onAnswer > dont wait open MainActivity - main will call TwilioVideoActivity
                                //else after press answer you only see TwilioVideoActivity no MainActivity in the stack and blank video screen - alpha not black cos it hasnt connected to anything
                                //------------------------------------------------------------------------------
                                Log.e(TAG, "TwilioVideoActivity.isActive() IS FALSE > launch MainActivity with no delay - it will launch TwilioVideoActivity");
                                //------------------------------------------------------------------------------
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                                getApplicationContext().startActivity(intent);
                                //------------------------------------------------------------------------------
                            }
                        }
                    };
                    handler.postDelayed(delayRunnable, 3000);
                    //------------------------------------------------------------------------------

            }

            @Override
            public void onReject() {
                Log.e(TAG, "[onCreateIncomingConnection] onReject: CALLED");
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);
                CordovaCall.sendJsonResult("reject", payload);
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "could not construct hangup payload", exception);
                    return;
                }
                CordovaCall.sendJsonResult("hangup", response);
            }
        };
        //------------------------------------------------------------------------------------------
        connection.setAddress(Uri.parse(payload.optString("callName")), TelecomManager.PRESENTATION_ALLOWED);
        //------------------------------------------------------------------------------------------
        Icon icon = CordovaCall.getIcon();
        if (icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence) "", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        conn = connection;
        CordovaCall.sendJsonResult("receiveCall", payload);
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("outgoingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "failed to create outgoing connection");
            return null;
        }

        final JSONObject payload = json;
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                super.onAnswer();
            }

            @Override
            public void onReject() {
                super.onReject();
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "could not construct hangup payload", exception);
                    return;
                }
                CordovaCall.sendJsonResult("hangup", response);
            }

            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                        }
                    }, 500);
                }
            }
        };
        connection.setAddress(Uri.parse(payload.optString("callName")), TelecomManager.PRESENTATION_ALLOWED);
        Icon icon = CordovaCall.getIcon();
        if (icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence) "", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        connection.setDialing();
        conn = connection;
        CordovaCall.sendJsonResult("sendCall", payload);
        return connection;
    }
}
