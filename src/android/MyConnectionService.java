package com.dmarc.cordovacall;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
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

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;

import org.apache.cordova.twiliovideo.TwilioVideoActivity;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import live.sea.chat.MainActivity;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";
    //private static Connection conn1;

    //LinkedHashMap keys are ordered - needed as getCollection - gets the last

    //private static HashMap<String, ArrayList<Connection>> connectionDictionary = new HashMap<String, ArrayList<Connection>>();

    private static ArrayList< Connection> connectionsList = new ArrayList<>();

    public static boolean isActive(Connection conn) {
        return conn != null && conn.getState() == Connection.STATE_ACTIVE;
    }

    public static boolean isEnded(Connection conn) {
        return conn == null || conn.getState() == Connection.STATE_DISCONNECTED;
    }

    public static boolean dropConnection(Connection conn, DisconnectCause cause) {
        if(isEnded(conn)) {
            return false;
        }

        conn.setDisconnected(cause);
        conn.destroy();
        removeConnection(conn);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {
        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("incomingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] failed to create incoming connection");
            return null;
        }

        final JSONObject payload = json;

        //BUG used to handle multiple 'endCall' messages coming from cordova till hangup returned
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java][onCreateIncomingConnection:] RESET FLAG TwilioVideoActivity.endCall_can_disconnect = true - next endCall will disconnect the VOIP and return 'hangup'");

        TwilioVideoActivity.endCall_can_disconnect = true;

        Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java][onCreateIncomingConnection:] Connection connection = new Connection(){onAnswer/onReject}");

        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] [onCreateIncomingConnection] onAnswer: CALLED");

                //----------------------------------------------------------------------------------
                //this shows the main Call UI for this connection - only work inside onAnswer
                //its the full screen callkit UI AFTER you ANSWER
                //THIS WILL NOT show the ANSWER/DECLINE popup
                this.setActive();

                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "answer" >> triggers Cordova to answerCall()
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] [onCreateIncomingConnection] onAnswer: RESPONSE to CORDOVA:'answer' - this should cause CORDOVA to start answerCall()");
                CordovaCall.sendJsonResult("answer", payload);
                // returning message 'answer'
                // will cause CORDOVA to call 'answerCall"
                // so do it asap

                //----------------------------------------------------------------------------------
                //Launch (after delay) so MainActivity + TwilioVideoActivity are ready
                //----------------------------------------------------------------------------------
                //CASE 1/2 - app running in foreground/background
                //call > onAnswer > wait 3 secs switch to TwilioVideoActivity


                //------------------------------------------------------------------------------
                //switch to TwilioVideoActivity after delay
                //------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] TwilioVideoActivity.isActive() IS TRUE > switch to TwilioVideoActivity after delay - START COUNTDOWN...");
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
                            Log.d(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] ...DELAY COMPLETE > switch to TwilioVideoActivity");
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
                            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] TwilioVideoActivity.isActive() IS FALSE > launch MainActivity with no delay - it will launch TwilioVideoActivity");
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
                //----------------------------------------------------------------------------------
                //onReject / DECLINE CALL button
                //----------------------------------------------------------------------------------
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] [onCreateIncomingConnection] onReject: CALLED");
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();

                //----------------------------------------------------------------------------------
                //conn = null;

                //removeConnection(payload);
                removeConnection(this);


                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);

                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "reject"
                //----------------------------------------------------------------------------------

                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] [onCreateIncomingConnection] onAnswer: RESPONSE to CORDOVA:'reject' - user pressed DECLINE");
                CordovaCall.sendJsonResult("reject", payload);

                //----------------------------------------------------------------------------------
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {

                //triggered from CORDOVA with "endCall"

                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);

                this.destroy();
                //----------------------------------------------------------------------------------
                //conn = null;


                //removeConnection(payload);
                removeConnection(this);

                //----------------------------------------------------------------------------------
                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] could not construct hangup payload", exception);
                    return;
                }

                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "hangup"
                //----------------------------------------------------------------------------------
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] [onCreateIncomingConnection] onDisconnect: RESPONSE to CORDOVA:'hangup'");
                //"hangup"returned in two place IncomingCall and OutgoinfCall - sea.chat doesnt do outgoign calls
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

        //------------------------------------------------------------------------------------------
        //conn = connection;

        //addConnection(payload, connection);
        addConnection(connection);

        //------------------------------------------------------------------------------------------
        //RESPONSE TO CORDOVA - 'receiveCall' - tells CORDOVA weve recived a VOIP call
        //------------------------------------------------------------------------------------------
        CordovaCall.sendJsonResult("receiveCall", payload);
        //------------------------------------------------------------------------------------------


        //ISSUE - ANDROID 10 for 2ndd, 3rd incoming calls the ACCEPT/DECLINE is hidden in the notification panel
        //or is behind the TwilioVA and MainActivity

        //------------------------------------------------------------------------------------------
        //        Intent intent = new Intent(this, com.android.incallui.InCallActivity.class);
        //        startActivity(intent);
        //------------------------------------------------------------------------------------------

        //connection.onShowIncomingCallUi();

        //This only works for
        if(null != TwilioVideoActivity.twilioVideoActivity){

            Lifecycle.State tvaState = TwilioVideoActivity.twilioVideoActivity.getLifecycle().getCurrentState();

            if(Lifecycle.State.CREATED == tvaState){
                Log.e(TAG, "TwilioVideoActivity STATE: CREATED");
            }
            else if(Lifecycle.State.STARTED == tvaState){
                Log.e(TAG, "TwilioVideoActivity STATE: STARTED");
            }
            else if(Lifecycle.State.RESUMED == tvaState){
                Log.e(TAG, "TwilioVideoActivity STATE: RESUMED");
            }
            else if(Lifecycle.State.INITIALIZED == tvaState){
                Log.e(TAG, "TwilioVideoActivity STATE: INITIALIZED");
            }
            else if(Lifecycle.State.DESTROYED == tvaState){
                Log.e(TAG, "TwilioVideoActivity STATE: DESTROYED");
            }
            else {
                Log.e(TAG, "TwilioVideoActivity STATE: unhandled");
            }

            TwilioVideoActivity.twilioVideoActivity.moveTaskToBack(true);

        }else{
            Log.e(TAG, "TwilioVideoActivity.twilioVideoActivity is null");
        }

        return connection;
    }






    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("outgoingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] failed to create outgoing connection");
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

                //----------------------------------------------------------------------------------
                //REMOVE OUTGOING -
                // see also above - REMOVE INCOMING CALL
                //conn = null;

                //removeConnection(payload);
                removeConnection(this);
                //----------------------------------------------------------------------------------


                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService.java] could not construct hangup payload", exception);
                    return;
                }
                //IN TWO PLACES - this is OUTGOING CALL - not used see above for INCOMING CALLS where sea.chat returns hangup
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

        //------------------------------------------------------------------------------------------
        //ADD OUTGOING CONNECTION - no used in seachat
        //conn = connection;

        //addConnection(payload, connection);
        addConnection(connection);

        //------------------------------------------------------------------------------------------
        //RESPONSE TO CORDOVA - 'sendCall'
        CordovaCall.sendJsonResult("sendCall", payload);
        //------------------------------------------------------------------------------------------
        return connection;
    }







    //----------------------------------------------------------------------------------------------
    //CALLS COLLECTION
    //----------------------------------------------------------------------------------------------
    //private static HashMap<String, ArrayList<Connection>> connectionDictionary = new HashMap<String, ArrayList<Connection>>();




    //----------------------------------------------------------------------------------------------
    //getConnection - gets last
    //----------------------------------------------------------------------------------------------
    public static Connection getOldestConnection() {
        Connection lastConnection = null;
        if(null != connectionsList){
            if(connectionsList.size() > 0){


                for(int index = 0; index < connectionsList.size(); index++) {
                    Connection connection = connectionsList.get(index);

                    int connectionState = connection.getState();
                    if(Connection.STATE_INITIALIZING == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_INITIALIZING");

                    }
                    else if(Connection.STATE_NEW == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_NEW");

                    }
                    else if(Connection.STATE_RINGING == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_RINGING");

                    }
                    else if(Connection.STATE_DIALING == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_DIALING");

                    }
                    else if(Connection.STATE_ACTIVE == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_ACTIVE");

                    }
                    else if(Connection.STATE_HOLDING == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_HOLDING");

                    }
                    else if(Connection.STATE_DISCONNECTED == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_DISCONNECTED");

                    }
                    else if(Connection.STATE_PULLING_CALL == connectionState){
                        Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][getOldestConnection] connection.getState(): STATE_PULLING_CALL");

                    }
                    else {
                        Log.e(TAG, "UNHANDLED connection.getState():" + connection.getState());
                    }

                }


                lastConnection = connectionsList.get(0);

            }else{
                Log.e(TAG, "connectionDictionary.size() is 0 - getConnection() FAILED");
            }
        }else{
            Log.e(TAG, "connectionsList is null");
        }

        return lastConnection;
    }




//    public static Connection getConnection() {
//        Connection lastConnection = null;
//
//        if(null != connectionDictionary){
//            Set<String> keys = connectionDictionary.keySet();
//            String[] keysArray = keys.toArray(new String[0]);
//
//            if(keysArray.length > 0 ){
//                String lastKey = keysArray[keysArray.length - 1];
//                if(null != lastKey){
//                    ArrayList<Connection> connectionListForKey = connectionDictionary.get(lastKey);
//                    if(null != connectionListForKey){
//                        //--------------------------------------------------------------------------
//                        //connectionListForKey
//                        //--------------------------------------------------------------------------
//                        if(connectionListForKey.size() > 0 ){
//                            //get LAST - should be only 1
//                            lastConnection = connectionListForKey.get(connectionListForKey.size() - 1);
//
//                        }else{
//                            Log.e(TAG, "connectionListForKey.size() is 0");
//                        }
//                    }else{
//                        Log.e(TAG, "connectionListForKey is null");
//                    }
//                }else{
//                    Log.e(TAG, "lastKey is null");
//                }
//
//            }else{
//                Log.e(TAG, " is null");
//            }
//
//        }else{
//            Log.e(TAG, "connectionsList is null");
//        }
//
//        return lastConnection;
//    }




    //----------------------------------------------------------------------------------------------
    //deinitConnection
    //----------------------------------------------------------------------------------------------
    //was in https://github.com/WebsiteBeaver/CordovaCall - but never used
    public static void deinitConnection() {
        //        conn1 = null;

//        if(null != connectionDictionary){
//            //nulls all objects in
//            connectionDictionary.clear();
//
//        }else{
//            Log.e(TAG, "connectionsList is null");
//        }

        if(null != connectionsList){
            connectionsList.clear();
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][deinitConnection] connectionsList is null - deinitConnection FAILED");
        }
    }


    //----------------------------------------------------------------------------------------------
    //CALLS COLLECTION - ADD / REMOVE
    //----------------------------------------------------------------------------------------------
    private void addConnection(Connection connection){
        if(null != connectionsList){
            if(null != connection){
                //----------------------------------------------------------------------------------
                connectionsList.add(connection);
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] DONE AFTER: connectionsList.size():" + connectionsList.size());

            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] connection is null - addConnection FAILED");
            }
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] connectionsList is null - addConnection FAILED");
        }
    }

    //callId is in payload
    private static void removeConnection(Connection connection){

        if(null != connection){
            //--------------------------------------------------------------------------------------
            if(null != connectionsList){
                //----------------------------------------------------------------------------------
                int indexFound = connectionsList.indexOf(connection);

                if(indexFound > -1){
                    connectionsList.remove(indexFound);
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] connectionsList.remove(indexFound) - DONE AFTER: size():" + connectionsList.size());

                }else{
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] indexOf(connection) is -1 - REMOVE FAILED -  NOT in connectionsList");
                }
                //----------------------------------------------------------------------------------
            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] connectionsList is null - removeConnection FAILED");
            }
            //--------------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] connection is null - removeConnection FAILED");
        }
    }

//    private void addConnection(JSONObject payload, Connection connection){
//        if(null != payload){
//            if(null != connection){
//
//                try {
//                    String callId = payload.getString("callId");
//
//                    if(null != callId){
//
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] START ***********************");
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection]  ");
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection]  callId: " + callId);
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection]  ");
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection]  END *************************" );
//
//
//                        ArrayList<Connection> connectionListForCallId = connectionDictionary.get(callId);
//                        if(null != connectionListForCallId){
//                            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] ERROR - connectionListForCallId is not null - should never happen");
//
//                        }else{
//                        	Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] connectionListForCallId is null - CREATE IT");
//
//                            connectionListForCallId = new ArrayList<Connection>();
//                        }
//
//                        connectionListForCallId.add(connection);
//
//                        connectionDictionary.put(callId, connectionListForCallId);
//
//                    }else{
//                    	Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] callId is null");
//                    }
//                } catch (JSONException exception) {
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] could not construct hangup payload", exception);
//
//                }
//                //----------------------------------------------------------------------------------
//
//            }else{
//                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] connection is null - addConnection FAILED");
//            }
//        }else{
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][addConnection] payload is null - addConnection FAILED");
//        }
//    }
//
//    //callId is in payload
//    private void removeConnection(JSONObject payload){
//
//        if(null != payload){
//            //----------------------------------------------------------------------------------
//            try {
//                String callId = payload.getString("callId");
//                if(null != callId){
//
//
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] START ***********************");
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection]");
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] callId: " + callId);
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection]");
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] END *************************" );
//
//
//                    ArrayList<Connection> connectionListForCallId = connectionDictionary.get(callId);
//                    if(null != connectionListForCallId){
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] ERROR - connectionListForCallId is not null - should never happen");
//
//                        if(1 == connectionListForCallId.size()){
//                            //once Connection fo this KEY call id
//                            //just remove it from the dict
//
//                            connectionDictionary.remove(callId);
//                            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] REMOVED OK by key callId:" + callId);
//
//                        }else{
//                            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] UNEXPECTED: connectionListForCallId.size() is not 1:" + connectionListForCallId.size());
//                        }
//
//
//                    }else{
//                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] ERROR - connectionListForCallId is null - SHOULD NEVER HAPPEN");
//
//
//                    }
//                }else{
//                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] callId is null");
//                }
//            } catch (JSONException exception) {
//                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] could not construct hangup payload", exception);
//            }
//            //----------------------------------------------------------------------------------
//        }else{
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService.java][removeConnection] payload is null - addConnection FAILED");
//        }
//    }

}
