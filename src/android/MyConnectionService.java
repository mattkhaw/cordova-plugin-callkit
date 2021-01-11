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
                this.setActive();
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);
                CordovaCall.sendJsonResult("answer", payload);
            }

            @Override
            public void onReject() {
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
        connection.setAddress(Uri.parse(payload.optString("callName")), TelecomManager.PRESENTATION_ALLOWED);
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
