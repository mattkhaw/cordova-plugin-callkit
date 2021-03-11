package com.dmarc.cordovacall;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;

import org.apache.cordova.twiliovideo.TwilioVideoActivity;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import live.sea.chat.MainActivity;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";

    private static LinkedHashMap<String, Connection> connectionDictionary = new LinkedHashMap<String, Connection>();

	//TODO - needed?
    //TwilioVideo
    private static int connectionAudioRoute = 0;

    public static int getConnectionAudioRoute() {
        return connectionAudioRoute;
    }

    public static boolean isActive(Connection conn) {
        return conn != null && conn.getState() == Connection.STATE_ACTIVE;
    }

    public static boolean isEnded(Connection conn) {
        return conn == null || conn.getState() == Connection.STATE_DISCONNECTED;
    }

    public static boolean dropConnection(Connection conn, DisconnectCause cause) {
        if(isEnded(conn)) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [dropConnection] isEnded(conn): TRUE - ended already SKIP destroy");

            return false;
        }

        Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [dropConnection] isEnded(conn): FALSE - conn.destroy();");
        conn.setDisconnected(cause);
        conn.destroy();


        //------------------------------------------------------------------------------------------

        //connection numbers usually be 0,1,2
        String removeable_callId = removeable_callId();

        Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [dropConnection] isEnded(conn): TRUE - removeConnectionByCallId(removeable_callId):" + removeable_callId);


        if(null != removeable_callId){
            removeConnectionByCallId(removeable_callId);
        }else{
        	Log.e(TAG, "currentConnectionCallId is null");
        }
        //------------------------------------------------------------------------------------------
        return true;
    }

    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {

        Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService][onCreateIncomingConnection:] NEW INCOMING CALL - START ****");

        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("incomingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] failed to create incoming connection");
            return null;
        }

        //------------------------------------------------------------------------------------------
        final JSONObject payload = json;

        //------------------------------------------------------------------------------------------
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService][onCreateIncomingConnection:] Connection connection = new Connection(){onAnswer/onReject}");

        final Connection connection1 = new Connection() {
            @Override
            public void onAnswer() {
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onAnswer: CALLED");

                //----------------------------------------------------------------------------------
                //this shows the main Call UI for this connection - only work inside onAnswer
                //its the full screen callkit UI AFTER you ANSWER
                //THIS WILL NOT show the ANSWER/DECLINE popup
                //----------------------------------------------------------------------------------
                this.setActive();
                //----------------------------------------------------------------------------------


                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "answer" >> triggers Cordova to answerCall()
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onAnswer: RESPONSE to CORDOVA:'answer' - this should cause CORDOVA to start answerCall()");
                CordovaCall.sendJsonResult("answer", payload);
                //----------------------------------------------------------------------------------
                // returning message 'answer'
                // will cause CORDOVA to call 'answerCall"
                // so do it asap
                //----------------------------------------------------------------------------------


                //----------------------------------------------------------------------------------
                //Launch (after delay) so MainActivity + TwilioVideoActivity are ready
                //----------------------------------------------------------------------------------
                //CASE 1/2 - app running in foreground/background
                //call > onAnswer > wait 3 secs switch to TwilioVideoActivity


                //------------------------------------------------------------------------------
                //switch to TwilioVideoActivity after delay
                //------------------------------------------------------------------------------
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] TwilioVideoActivity.isActive() IS TRUE > switch to TwilioVideoActivity after delay - START COUNTDOWN...");
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
                            Log.d(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] ...DELAY COMPLETE > switch to TwilioVideoActivity");
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
                            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] TwilioVideoActivity.isActive() IS FALSE > launch MainActivity with no delay - it will launch TwilioVideoActivity");
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
            public void onAnswer(int videoState) { 
                // called on Android 11 MIUI 12 instead of default onAnswer
                Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onAnswer(with videoState): CALLED. videoState: " + videoState);
                onAnswer();
            }

            @Override
            public void onReject() {
                //----------------------------------------------------------------------------------
                //onReject / DECLINE CALL button
                //----------------------------------------------------------------------------------
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onReject: CALLED");
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();

                //----------------------------------------------------------------------------------
                removeConnection(payload);


                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);

                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "reject"
                //----------------------------------------------------------------------------------

                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onAnswer: RESPONSE to CORDOVA:'reject' - user pressed DECLINE");
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
                //INCOMING - also in OUTGOING BELOW
                removeConnection(payload);

                //----------------------------------------------------------------------------------
                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] could not construct hangup payload", exception);
                    return;
                }

                //----------------------------------------------------------------------------------
                //RESPOND TO CORDOVA - "hangup"
                //----------------------------------------------------------------------------------
                Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] [onCreateIncomingConnection] onDisconnect: RESPONSE to CORDOVA:'hangup'");
                //"hangup"returned in two place IncomingCall and OutgoinfCall - sea.chat doesnt do outgoign calls
                CordovaCall.sendJsonResult("hangup", response);
            }

			//AUDIO/SPEAKERPHONE/BLUETOOTH
            public void onCallAudioStateChanged (CallAudioState state){
                Log.e(TAG, "onCallAudioStateChanged: state" + state );

                switch(state.getRoute()){
                    case CallAudioState.ROUTE_EARPIECE:
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_EARPIECE");

                        connectionAudioRoute = CallAudioState.ROUTE_EARPIECE;

                        break;
                    case CallAudioState.ROUTE_BLUETOOTH:
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_BLUETOOTH");

                        connectionAudioRoute = CallAudioState.ROUTE_BLUETOOTH;

                        break;
                    case CallAudioState.ROUTE_WIRED_HEADSET:
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_WIRED_HEADSET (headphones without mic)");

                        connectionAudioRoute = CallAudioState.ROUTE_WIRED_HEADSET;

                        break;
                    case CallAudioState.ROUTE_SPEAKER:
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_SPEAKER");

                        connectionAudioRoute = CallAudioState.ROUTE_SPEAKER;

                        break;
                    default:
                        Log.e(TAG, "onCallAudioStateChanged: UNHANDLED" + state );

                        connectionAudioRoute = 0;
                        break;
                }
                //the number of ROUTES is less than DEVICES

                logAudioDeviceInfo();

            }
        };

        //------------------------------------------------------------------------------------------
        connection1.setAddress(Uri.parse(payload.optString("callName")), TelecomManager.PRESENTATION_ALLOWED);

        //------------------------------------------------------------------------------------------
        Icon icon = CordovaCall.getIcon();
        if (icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence) "", icon, new Bundle());
            connection1.setStatusHints(statusHints);
        }

        //------------------------------------------------------------------------------------------
        addConnection(payload, connection1);

        //------------------------------------------------------------------------------------------
        //RESPONSE TO CORDOVA - 'receiveCall' - tells CORDOVA weve recived a VOIP call
        //------------------------------------------------------------------------------------------
        CordovaCall.sendJsonResult("receiveCall", payload);

        //------------------------------------------------------------------------------------------
        //ANDROID 10 - possibly still issue
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

            //--------------------------------------------------------------------------------------
            //DEBUG - I was checking state to see if I could remove connection instead we pass endCall(callId)
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
            //--------------------------------------------------------------------------------------
            TwilioVideoActivity.twilioVideoActivity.moveTaskToBack(true);
            //--------------------------------------------------------------------------------------
        }else{
        	Log.e(TAG, "TwilioVideoActivity.twilioVideoActivity is null");
        }



        //------------------------------------------------------------------------------------------
        //TO TOGGLE  SPEAKER DURING VOIP CALL ROUTE_SPEAKER or ROUTE_EARPIECE - setSpeakerPhoneOn(t/f) - doe nothing during VOIP call
        connection1.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        //------------------------------------------------------------------------------------------
        //note: setAudioRoute is async - calling isSpeakerPhoneOn() here may not return correct value  - use onCallAudioStateChanged
        //------------------------------------------------------------------------------------------

        CallAudioState callAudioStateAFTER = connection1.getCallAudioState();

        if(null != callAudioStateAFTER){
            switch(callAudioStateAFTER.getRoute()){
                case CallAudioState.ROUTE_EARPIECE:
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_EARPIECE");
                    break;
                case CallAudioState.ROUTE_BLUETOOTH:
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_BLUETOOTH");
                    break;
                case CallAudioState.ROUTE_WIRED_HEADSET:
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_WIRED_HEADSET");
                    break;
                case CallAudioState.ROUTE_SPEAKER:
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO] CordovaCall onCallAudioStateChanged: ROUTE_SPEAKER");
                    break;
                default:
                    Log.e(TAG, "onCallAudioStateChanged: UNHANDLED" );
                    break;
            }
        }else{
        	Log.e(TAG, "callAudioStateAFTER is null");
        }
        //------------------------------------------------------------------------------------------
        Log.w(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService][onCreateIncomingConnection:] RETURN Connection for NEW INCOMING CALL - shows CALLKIT ui END  ****");

        return connection1;
    }






    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        JSONObject json = null;
        try {
            json = new JSONObject(request.getExtras().getString("outgoingCall"));
        } catch (JSONException e) {
            Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] failed to create outgoing connection");
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
                removeConnection(payload);
                //----------------------------------------------------------------------------------


                JSONObject response = new JSONObject();
                try {
                    response.put("callId", payload.getString("callId"));
                    response.put("callName", payload.getString("callName"));
                } catch (JSONException exception) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN] [MyConnectionService] could not construct hangup payload", exception);
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
        addConnection(payload, connection);


        //------------------------------------------------------------------------------------------
        //RESPONSE TO CORDOVA - 'sendCall'
        CordovaCall.sendJsonResult("sendCall", payload);
        //------------------------------------------------------------------------------------------
        return connection;
    }


    //----------------------------------------------------------------------------------------------
    //CALLS COLLECTION
    //----------------------------------------------------------------------------------------------

    public static Connection getConnectionByCallId(String callId){
        Connection connectionFound = null;

        if(null != callId){

            //--------------------------------------------------------------------------------------
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] START ***********************");
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] callId: " + callId);
            //--------------------------------------------------------------------------------------

            connectionFound = connectionDictionary.get(callId);

            if(null != connectionFound){
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] connectionFound: FOUND" );
            }else{
                //ok if endCall/hangUp done already
                //can happen if you kill app and restart during debugging while call on web still open
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] connectionFound: NOT FOUND (ok if endCall/hangUp done already)" );
            }

            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] END *************************" );
        }else{
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getConnectionByCallId] callId is null");
        }

        return connectionFound;
    }

    //----------------------------------------------------------------------------------------------
    //deinitConnection
    //----------------------------------------------------------------------------------------------
    //was in https://github.com/WebsiteBeaver/CordovaCall - but never used
    public static void deinitConnection() {

        if(null != connectionDictionary){
            //nulls all objects in
            connectionDictionary.clear();

        }else{
            Log.e(TAG, "connectionsList is null");
        }

    }


    //----------------------------------------------------------------------------------------------
    //CALLS COLLECTION - ADD / REMOVE
    //----------------------------------------------------------------------------------------------
//    public static Connection getLatestConnection() {
//        Connection latestConnection = null;
//
//        if(null != currentConnectionCallId){
//            //--------------------------------------------------------------------------
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLatestConnection] START ***********************");
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLatestConnection]  ");
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLatestConnection]  this.currentConnectionCallId: " + currentConnectionCallId);
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLatestConnection]  ");
//
//            //--------------------------------------------------------------------------
//            latestConnection = getConnectionByCallId(currentConnectionCallId);
//            //--------------------------------------------------------------------------
//
//        }else{
//            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] callId is null - ok if first video call");
//        }
//
//        return latestConnection;
//    }


    public static Connection getLastConnection() {
        Connection latestConnection = null;
        
        String callId_latest = last_callId();
        
        if(null != callId_latest){
            //--------------------------------------------------------------------------
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLastConnection] START ***********************");
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLastConnection]  ");
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLastConnection]  last_callId(): " + callId_latest);
            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLastConnection]  ");

            //--------------------------------------------------------------------------
            latestConnection = getConnectionByCallId(callId_latest);
            //--------------------------------------------------------------------------

        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getLastConnection] callId is null - ok if first video call");
        }

        return latestConnection;
    }

    public static Connection getRemoveableConnection() {
        Connection latestConnection = null;

        String removeable_callId = removeable_callId();

        if(null != removeable_callId){
            //--------------------------------------------------------------------------
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getRemoveableConnection] START ***********************");
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getRemoveableConnection]  ");
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getRemoveableConnection]  removeable_callId(): " + removeable_callId);
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getRemoveableConnection]  ");

            //--------------------------------------------------------------------------
            latestConnection = getConnectionByCallId(removeable_callId);
            //--------------------------------------------------------------------------

        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][getRemoveableConnection] callId is null - ok if first video call");
        }

        return latestConnection;
    }

    
    
    //--------------------------------------------------------------------------------------
    //FIRST/LAST callIds
    //--------------------------------------------------------------------------------------
    public static Object first_callId(){
        String first_callId = null;

        //set of keys
        Set<String> set = connectionDictionary.keySet();

        //Object[]
        Object[] array = set.toArray();

        Log.e(TAG, "getFirst: set.toArray():" + array.length);

        if(array.length > 0){

            Object firstObject = array[0];
            if(firstObject instanceof String){
                first_callId = (String)firstObject;
            }else{
                Log.e(TAG, "getFirst:firstObject instanceof String FAILED");
            }
        }else{
            Log.e(TAG, "getFirst:array.length is 0 - firstInteger failed");
        }

        Log.e(TAG, "getFirst: array:" + array.length);
        return first_callId;
    }
    
    
    public static String last_callId(){
        String lastKey = null;

        //set of keys
        Set<String> set = connectionDictionary.keySet();

        //Object[]
        Object[] array = set.toArray();

        Log.e(TAG, "lastInteger: set.toArray():" + array.length);

        if(array.length > 0){
            Object lastObject = array[array.length - 1];
            if(lastObject instanceof String){
                lastKey = (String)lastObject;
            }else{
                Log.e(TAG, "lastObject instanceof String FAILED");
            }

        }else{
            Log.e(TAG, "lastInteger: array.length is 0 - lastInteger failed");
        }
        
        return lastKey;
    }
    public static String removeable_callId(){
        String callIdFound = null;

        //set of keys
        Set<String> set = connectionDictionary.keySet();

        //Object[]
        Object[] array = set.toArray();

        Log.e(TAG, "removeable_callId: set.toArray():" + array.length);
        

        if(array.length == 0){
            Log.e(TAG, "removeable_callId: array.length is 0 - removeableConnection_callId failed");

        }
        else if(array.length == 1){
            Log.e(TAG, "removeable_callId: array.length is 1 - return array[0]");

            Object firstObject = array[0]; //FIRST

            if(firstObject instanceof String){
                callIdFound = (String)firstObject;
            }else{
                Log.e(TAG, "removeable_callId: array[0] instanceof String FAILED");
            }
        }
        else if(array.length > 1){
            Log.e(TAG, "removeable_callId: array.length > 1 - remove 2nd last");

            //length: 0 - return null
            //length: 1 - return first      >> [0]
            //length: 2 - return length - 2 >> [1]
            //length: 3 - return length - 2 >> [2]
            Object secondLastObject = array[array.length - 2];

            if(secondLastObject instanceof String){
                callIdFound = (String)secondLastObject;
            }else{
                Log.e(TAG, "lastObject instanceof String FAILED");
            }
        }
        else{
            Log.e(TAG, "removeable_callId: UNHANDLED array.length" + array.length);
        }
        
        return callIdFound;
    }


    //----------------------------------------------------------------------------------------------
    //CALLS COLLECTION - ADD / REMOVE
    //----------------------------------------------------------------------------------------------
    private static void addConnection(JSONObject payload, Connection connection){
        if(null != payload){
            if(null != connection){

                try {
                    String callId = payload.getString("callId");

                    if(null != callId){
                        //--------------------------------------------------------------------------
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] START ***********************");
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection]  ");
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection]  callId: " + callId);
                        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection]  ");


                        //--------------------------------------------------------------------------
                        Connection connectionForCallId = connectionDictionary.get(callId);
                        if(null != connectionForCallId){
                            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] ERROR - connectionForCallId is not null - callId added already");

                        }else{
                            //----------------------------------------------------------------------
                            //ADD TO CONNECTIONS
                            //----------------------------------------------------------------------

                            connectionDictionary.put(callId, connection);

                            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] AFTER connectionDictionary.keySet().size():" + connectionDictionary.keySet().size());
                            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] ADDED  callId: " + callId);
                            Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] ADDED  *****");

                        }
                        //--------------------------------------------------------------------------

                    }else{
                    	Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] callId is null");
                    }
                } catch (JSONException exception) {
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] could not construct hangup payload", exception);

                }
                //----------------------------------------------------------------------------------

            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] connection is null - addConnection FAILED");
            }
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] payload is null - addConnection FAILED");
        }

        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][addConnection] END *************************" );
    }

    //callId is in payload
    private static void removeConnection(JSONObject payload){

        if(null != payload){
            //----------------------------------------------------------------------------------
            try {
                String callId = payload.getString("callId");
                if(null != callId){


                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] START ***********************");
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection]");
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] callId: " + callId);
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection]");

                    removeConnectionByCallId(callId);

                }else{
                    Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] callId is null");
                }
            } catch (JSONException exception) {
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] could not construct hangup payload", exception);
            }
            //----------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] payload is null - addConnection FAILED");
        }
        Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnection] END *************************" );
    }

    private static void removeConnectionByCallId(String callId){

        if(null != callId){


            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] START ***********************");
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId]");
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] callId: " + callId);
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId]");



            Connection connectionForCallId = connectionDictionary.get(callId);
            if(null != connectionForCallId){

                connectionDictionary.remove(callId);
                Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] REMOVED callId:" + callId);
                Log.w(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] REMOVED connectionDictionary.keySet().size():" + connectionDictionary.keySet().size());


            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] ERROR - CANT REMOVE -  connectionListForCallId is null/NOT FOUND for callId:" + callId);

            }

        }else{
            Log.e(TAG, "[VOIPCALLKITPLUGIN][MyConnectionService][removeConnectionByCallId] callId is null");
        }
    }



    //Debug as we plug in wired and unwrired headset/ bluetooth headphones
    //https://github.com/dengjianzhong/OddPoint/blob/2296ebc93392f8f8c98fa94bc30a4e049bf917b4/libwebrtc/src/main/java/sdk/android/src/java/org/webrtc/audio/WebRtcAudioUtils.java
    private void logAudioDeviceInfo() {
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][logAudioDeviceInfo]");

        if (Build.VERSION.SDK_INT < 23) {
            Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] Build.VERSION.SDK_INT < 23 - SKIP");

        }else{
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if(null != audioManager){
                final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);

                Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] Audio Devices:" + devices.length);

                for (AudioDeviceInfo deviceInfo : devices) {
                    //------------------------------------------------------------------------------
                    //Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] deviceInfo: START ********");
                    String io = "";
                    if(deviceInfo.isSource()){
                        if(deviceInfo.isSink()){
                            io = "Error(isSource:true and isSink:true)";
                        }else{
                            io = "INPUT ";//space needed for column
                        }
                    }else{
                        //source false
                        if(deviceInfo.isSink()){
                            io = "OUTPUT";
                        }else{
                            io = "Error(isSource:false and isSink:false)";
                        }
                    }

                    Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] [" + io +  "] [Name:'" + deviceInfo.getProductName()+ "'] "  + deviceTypeToString(deviceInfo.getType()));

                    //    if (deviceInfo.getChannelCounts().length > 0) {
                    //        Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] deviceInfo: channels        :" + Arrays.toString(deviceInfo.getChannelCounts()));
                    //    }
                    //
                    //    if (deviceInfo.getEncodings().length > 0) {
                    //        // Examples: ENCODING_PCM_16BIT = 2, ENCODING_PCM_FLOAT = 4.
                    //        Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] deviceInfo: encodings       :" + Arrays.toString(deviceInfo.getEncodings()));
                    //    }
                    //
                    //    if (deviceInfo.getSampleRates().length > 0) {
                    //        Log.w(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] deviceInfo: samplerates     :" + Arrays.toString(deviceInfo.getSampleRates()));
                    //
                    //    }
                    //------------------------------------------------------------------------------

                }
            }else{
                Log.e(TAG, "[VOIPCALLKITPLUGIN][AUDIO][logAudioDeviceInfo] audioManager is null");
            }
        }
    }
    // Converts AudioDeviceInfo types to local string representation.
    private static String deviceTypeToString(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_UNKNOWN:
                return "TYPE_UNKNOWN";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "TYPE_BUILTIN_EARPIECE";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "TYPE_BUILTIN_SPEAKER";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "TYPE_WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "TYPE_WIRED_HEADPHONES";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "TYPE_LINE_ANALOG";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "TYPE_LINE_DIGITAL";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "TYPE_BLUETOOTH_SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "TYPE_BLUETOOTH_A2DP";
            case AudioDeviceInfo.TYPE_HDMI:
                return "TYPE_HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "TYPE_HDMI_ARC";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "TYPE_USB_DEVICE";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "TYPE_USB_ACCESSORY";
            case AudioDeviceInfo.TYPE_DOCK:
                return "TYPE_DOCK";
            case AudioDeviceInfo.TYPE_FM:
                return "TYPE_FM";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "TYPE_BUILTIN_MIC";
            case AudioDeviceInfo.TYPE_FM_TUNER:
                return "TYPE_FM_TUNER";
            case AudioDeviceInfo.TYPE_TV_TUNER:
                return "TYPE_TV_TUNER";
            case AudioDeviceInfo.TYPE_TELEPHONY:
                return "TYPE_TELEPHONY";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "TYPE_AUX_LINE";
            case AudioDeviceInfo.TYPE_IP:
                return "TYPE_IP";
            case AudioDeviceInfo.TYPE_BUS:
                return "TYPE_BUS";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "TYPE_USB_HEADSET";
            default:
                return "TYPE UNHANDLED ";
        }
    }



}
