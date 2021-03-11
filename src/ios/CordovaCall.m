#import "CordovaCall.h"
#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>

@implementation CordovaCall

@synthesize VoIPPushCallbackId, VoIPPushClassName, VoIPPushMethodName;

BOOL hasVideo = NO;
NSString* appName;
NSString* ringtone;
NSString* icon;
NSString* eventCallbackId;
BOOL includeInRecents = NO;
BOOL monitorAudioRouteChange = NO;
BOOL enableDTMF = NO;
NSMutableDictionary* callsMetadata;

#pragma mark -
#pragma mark China
#pragma mark -
- (BOOL)isCallKitDisabledForChina{
    BOOL isCallKitDisabledForChina = FALSE;
    
    NSLocale *currentLocale = [NSLocale currentLocale];
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale.countryCode:'%@'", currentLocale);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale.localeIdentifier:'%@'", currentLocale.localeIdentifier);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale.countryCode:'%@'", currentLocale.countryCode);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale.languageCode:'%@'", currentLocale.languageCode);
    
    if ([currentLocale.countryCode containsString: @"CN"] || [currentLocale.countryCode containsString: @"CHN"]) {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale is China so we CANNOT use CallKit.");
        isCallKitDisabledForChina = TRUE;
        
    } else {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCallKitDisabledForChina: currentLocale is NOT China(CN/CHN) so we CAN use CallKit.");
        isCallKitDisabledForChina = FALSE;
    }
    
    return isCallKitDisabledForChina;
}

- (void)pluginInitialize
{
    //SETUP as EARLY AS POSSIBLE - as iOS change it for mode:VoiceChat or mode:VideoChat
    //detect Audio Route Changes to make speakerOn and speakerOff event handlers
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleAudioRouteChange:)
                                                 name:AVAudioSessionRouteChangeNotification
                                               object:nil];
    
    
    //CALLKIT banned in china
    NSLocale *currentLocale = [NSLocale currentLocale];
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] pluginInitialize: currentLocale.countryCode:'%@'", currentLocale);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] pluginInitialize: currentLocale.localeIdentifier:'%@'", currentLocale.localeIdentifier);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] pluginInitialize: currentLocale.countryCode:'%@'", currentLocale.countryCode);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] pluginInitialize: currentLocale.languageCode:'%@'", currentLocale.languageCode);
    
    
    
    
    CXProviderConfiguration *providerConfiguration;
    appName = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleDisplayName"];
    
    providerConfiguration = [[CXProviderConfiguration alloc] initWithLocalizedName:appName];
    providerConfiguration.maximumCallGroups = 1;
    providerConfiguration.maximumCallsPerCallGroup = 1;
    
    NSMutableSet *handleTypes = [[NSMutableSet alloc] init];
    [handleTypes addObject:@(CXHandleTypePhoneNumber)];
    providerConfiguration.supportedHandleTypes = handleTypes;
    
    providerConfiguration.supportsVideo = YES;
    
    if (@available(iOS 11.0, *)) {
        providerConfiguration.includesCallsInRecents = NO;
    }
    
    //CHINA
    if ([self isCallKitDisabledForChina]) {
        NSLog(@"currentLocale is China so we cannot use CallKit.  self.provider = nil");
        //Will stop the ALERT/DECLINE VOIP UI form APPEARING
        self.provider = nil;
    } else {
        NSLog(@"currentLocale is NOT China(CN/CHN) so we cannot use CallKit.");
        
        // setup CallKit observer
        self.provider = [[CXProvider alloc] initWithConfiguration:providerConfiguration];
        [self.provider setDelegate:self queue:nil];
    }

    //----------------------------------------------------------------------------------------------
    
    self.callController = [[CXCallController alloc] init];
    callsMetadata = [[NSMutableDictionary alloc]initWithCapacity:5];
    
    //allows user to make call from recents
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(receiveCallFromRecents:) name:@"RecentsCallNotification" object:nil];
   
}

// CallKit - Interface
- (void)init:(CDVInvokedUrlCommand*)command
{
    eventCallbackId = command.callbackId;
}

- (void)updateProviderConfig
{
    CXProviderConfiguration *providerConfiguration;
    providerConfiguration = [[CXProviderConfiguration alloc] initWithLocalizedName:appName];
    providerConfiguration.maximumCallGroups = 1;
    providerConfiguration.maximumCallsPerCallGroup = 1;
   
    if(ringtone != nil) {
        providerConfiguration.ringtoneSound = ringtone;
    }
    
    if(icon != nil) {
        UIImage *iconImage = [UIImage imageNamed:icon];
        NSData *iconData = UIImagePNGRepresentation(iconImage);
        providerConfiguration.iconTemplateImageData = iconData;
    }
    
    NSMutableSet *handleTypes = [[NSMutableSet alloc] init];
    [handleTypes addObject:@(CXHandleTypePhoneNumber)];
    providerConfiguration.supportedHandleTypes = handleTypes;
    
    providerConfiguration.supportsVideo = hasVideo;
    
    if (@available(iOS 11.0, *)) {
        providerConfiguration.includesCallsInRecents = includeInRecents;
    }
    
    //CHINA
    if(self.provider){
        self.provider.configuration = providerConfiguration;
    }else{
        NSLog(@"self.provider is NULL - CANT SET self.provider.configuration - is user in CHINA/CN/CHN");
    }
    
}

#pragma mark -
#pragma mark setupAudioSession
#pragma mark -

- (void)setupAudioSession
{
    @try {
        AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
        //------------------------------------------------------------------------------------------
        NSError* error_setCategory= nil;
        
        NSLog(@"[AUDIO][SET CATEGORY] CordovaCall.m setupAudioSession setCategory:AVAudioSessionCategoryPlayAndRecord");


        NSLog(@"[setupAudioSession] setCategory:AVAudioSessionCategoryPlayAndRecord START ********");
        
        if(![sessionInstance setCategory:AVAudioSessionCategoryPlayAndRecord
                                          error:&error_setCategory] )
        {
            if (error_setCategory != nil) {
                NSLog(@"[setupAudioSession] setCategory:AVAudioSessionCategoryPlayAndRecord error_setCategory: %@", error_setCategory);
                
            } else {
                NSLog(@"[setupAudioSession] setCategory:AVAudioSessionCategoryPlayAndRecordOK");
            }
        }else{
            NSLog(@"[setupAudioSession] setCategory:AVAudioSessionCategoryPlayAndRecord OK - END ********");
        }
        
        //------------------------------------------------------------------------------------------
        //VoiceChat OR VideoChat (SPEAKERS ON by default - cant turn off)
        //------------------------------------------------------------------------------------------
        //SPEAKERPHONE/RECEIVER(earpiece)
        //------------------------------------------------------------------------------------------
        //in original twilio sample but doesnt show Speaker when we tap on airplay picker
        /*! Only valid with AVAudioSessionCategoryPlayAndRecord.  Appropriate for Voice over IP
         (VoIP) applications.  Reduces the number of allowable audio routes to be only those
         that are appropriate for VoIP applications and may engage appropriate system-supplied
         signal processing.  Has the side effect of setting AVAudioSessionCategoryOptionAllowBluetooth */
        //SEE ALSO https://developer.apple.com/library/archive/qa/qa1803/_index.html
        //---------------------------------------------------------
        //WRONG - DONT use AVAudioSessionModeVideoChat - SPEAKER on by default
        //[sessionInstance setMode:AVAudioSessionModeVideoChat error:nil];
        //---------------------------------------------------------
        [sessionInstance setMode:AVAudioSessionModeVoiceChat error:nil];
        //------------------------------------------------------------------------------------------
        
        
        //------------------------------------------------------------------------------------------
        //   //https://github.com/iFLYOS-OPEN/SDK-EVS-iOS/blob/a111b7765fab62586be72199c417e2b103317e44/Pod/Classes/common/media_player/AudioSessionManager.m
        //   [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker|AVAudioSessionCategoryOptionMixWithOthers error:nil];
        //   [[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:nil];
        //   [[AVAudioSession sharedInstance] setActive:YES error:nil];
        //------------------------------------------------------------------------------------------
    
        NSTimeInterval bufferDuration = .005;
        [sessionInstance setPreferredIOBufferDuration:bufferDuration error:nil];
        [sessionInstance setPreferredSampleRate:44100 error:nil];
        
        //----------------------------------------------------------------------------------
        //Turn on SPEAKER initially
        //----------------------------------------------------------------------------------
        //For INCOMING CALLS - this is called by
        
        //we removed options:AVAudioSessionCategoryOptionDefaultToSpeaker so EARPICE is default for VoiceChat

        //POSSIBLE ISSUE on OUTGOING CALL - changing category seems to kill ringin.mp3
        //I added 2 sec delay before playing it till all AV Category changes have completed
                
        NSLog(@"[CordovaCall.m][setupAudioSession:] [AVAudioSession sharedInstance].currentRoute:\r%@", [AVAudioSession sharedInstance].currentRoute);

        //------------------------------------------------------------------------------------------
        //TURN ON SPEAKER FOR VIDEO CALLS (unless Bluetooth already connected)
        //------------------------------------------------------------------------------------------
        
        //CAN BE CALLED TWICE - depending on direction of the call - search for overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker
        
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][AVAudioSession sharedInstance].currentRoute:\r%@", [AVAudioSession sharedInstance].currentRoute);
        if([self isCurrentAudioRouteOutputSetToBluetooth])
        {
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] isCurrentAudioRouteOutputSetToBluetooth: TRUE - DONT TURN ON SPEAKER");
            //------------------------------------------------------------------------------
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] isCurrentAudioRouteOutputSetToBluetooth: FALSE - OK TO TURN ON SPEAKER");
            
            //------------------------------------------------------------------------------
            //Turn on SPEAKER initially
            //------------------------------------------------------------------------------
            //we removed options:AVAudioSessionCategoryOptionDefaultToSpeaker so EARPICE is default for VoiceChat
            NSError *error_overrideOutputAudioPort = nil;
            if (![[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker
                                                                    error:&error_overrideOutputAudioPort])
            {
                NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] setupAudioSession: AVAudioSessionPortOverrideSpeaker FAILED: %@",error_overrideOutputAudioPort);
            }else{
                NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] setupAudioSession: AVAudioSessionPortOverrideSpeaker OK");
            }
            
            //setActive: is NOT needed - i think since iOS7
            
        }
        
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] setupAudioSession: END");
    }
    @catch (NSException *exception) {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] setupAudioSession: Unknown error returned from setupAudioSession");
    }
    return;
}

//method defined in two places becuase two different plugins
//USED FOR - DONT TURN ON SPEAKER if BLUETOOTH HEADSET plugged in WHEN CHAT STARTS
- (BOOL)isCurrentAudioRouteOutputSetToBluetooth {
    
    BOOL _isCurrentAudioRouteOutputSetToBluetooth = FALSE;
    AVAudioSessionRouteDescription *currentRoute = [AVAudioSession sharedInstance].currentRoute;
    
    //----------------------------------------------------------------------------------------------
    //OUTPUTS
    //----------------------------------------------------------------------------------------------
    NSArray<AVAudioSessionPortDescription *> *outputs = currentRoute.outputs;
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][isCurrentAudioRouteOutputSetToBluetooth] CALLED: [outputs count]:%ld", [outputs count]);
    
    if ([outputs count] > 0) {
        //------------------------------------------------------------------------------------------
        AVAudioSessionPortDescription *output = [outputs objectAtIndex:0]; //ususally only 1
        
        if ([[output portType] isEqualToString:AVAudioSessionPortBuiltInReceiver]) {
            //--------------------------------------------------------------------------------------
            //EARPIERCE / Receiver / iPhone
            //--------------------------------------------------------------------------------------
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][isCurrentAudioRouteOutputSetToBluetooth] AVAudioSessionPortBuiltInReceiver - EARPIECE");
            
            
        }
        else if ([[output portType] isEqualToString:AVAudioSessionPortBuiltInSpeaker]) {
            //--------------------------------------------------------------------------------------
            //Speaker
            //--------------------------------------------------------------------------------------
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][isCurrentAudioRouteOutputSetToBluetooth] AVAudioSessionPortBuiltInSpeaker - SPEAKER");
            //--------------------------------------------------------------------------------------
        }
        else if ([[output portType] isEqualToString:AVAudioSessionPortBluetoothA2DP] ||
                 [[output portType] isEqualToString:AVAudioSessionPortBluetoothLE] ||
                 [[output portType] isEqualToString:AVAudioSessionPortBluetoothHFP]
                 )
        {
            //--------------------------------------------------------------------------------------
            //BLUETOOTH
            //--------------------------------------------------------------------------------------
            // AVAudioSessionPortBluetoothA2DP - Output on a Bluetooth A2DP device //AIRPODS
            // AVAudioSessionPortBluetoothLE   - Output on a Bluetooth Low Energy device
            // AVAudioSessionPortBluetoothHFP  - Input or output on a Bluetooth Hands-Free Profile device
            //--------------------------------------------------------------------------------------
            //EXAMPLES
            //Airpods v1
            //    "<AVAudioSessionPortDescription: 0x281cfc0f0, type = BluetoothA2DPOutput; name = Brian\U2019s AirPods; UID = D4:90:9C:A3:A7:2B-tacl; selectedDataSource = (null)>"
            //--------------------------------------------------------------------------------------
            //bose Quiet Control
            //NEW >> OUTPUT: portName:Bose QuietControl 30 portType:BluetoothHFP
            //--------------------------------------------------------------------------------------
            
            //--------------------------------------------------------------------------------------
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][isCurrentAudioRouteOutputSetToBluetooth] NEW >> AVAudioSessionPortBuiltInSpeaker:[portType:'%@'] - BLUETOOTH HEADSET", [output portType] );
            //--------------------------------------------------------------------------------------
            _isCurrentAudioRouteOutputSetToBluetooth = TRUE;
            //--------------------------------------------------------------------------------------
        }
        else
        {
            //--------------------------------------------------------------------------------------
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][routeChange] NEW >> OUTPUT: portName:%@ portType:%@",[output portName], [output portType]);
            //--------------------------------------------------------------------------------------
        }
    }else{
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO][routeChange] CALLED: [outputs count] == 0");
    }
    
    return _isCurrentAudioRouteOutputSetToBluetooth;
}





- (void)setAppName:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* proposedAppName = [command.arguments objectAtIndex:0];
    
    if (proposedAppName != nil && [proposedAppName length] > 0) {
        appName = proposedAppName;
        [self updateProviderConfig];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"App Name Changed Successfully"];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"App Name Can't Be Empty"];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setIcon:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* proposedIconName = [command.arguments objectAtIndex:0];
    
    if (proposedIconName == nil || [proposedIconName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Icon Name Can't Be Empty"];
    } else if([UIImage imageNamed:proposedIconName] == nil) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"This icon does not exist. Make sure to add it to your project the right way."];
    } else {
        icon = proposedIconName;
        [self updateProviderConfig];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Icon Changed Successfully"];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setRingtone:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* proposedRingtoneName = [command.arguments objectAtIndex:0];
    
    if (proposedRingtoneName == nil || [proposedRingtoneName length] == 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ringtone Name Can't Be Empty"];
    } else {
        ringtone = [NSString stringWithFormat: @"%@.caf", proposedRingtoneName];
        [self updateProviderConfig];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ringtone Changed Successfully"];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setIncludeInRecents:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    includeInRecents = [[command.arguments objectAtIndex:0] boolValue];
    [self updateProviderConfig];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"includeInRecents Changed Successfully"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setDTMFState:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    enableDTMF = [[command.arguments objectAtIndex:0] boolValue];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"enableDTMF Changed Successfully"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setVideo:(CDVInvokedUrlCommand*)command
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] setVideo: CALLED");
    
    CDVPluginResult* pluginResult = nil;
    hasVideo = [[command.arguments objectAtIndex:0] boolValue];
    [self updateProviderConfig];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"hasVideo Changed Successfully"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark -
#pragma mark VOIP - INCOMING - receiveCall
#pragma mark -
- (void)receiveCall:(CDVInvokedUrlCommand*)command
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall: CALLED - REMOTE USER CALLS IOS");
    
    NSDictionary *incomingCall = [command.arguments objectAtIndex:0];
    if (incomingCall == nil) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Call is not defined"] callbackId:command.callbackId];
    }
    
    BOOL hasId = ![incomingCall[@"callId"] isEqual:[NSNull null]];
    NSString* callName = incomingCall[@"callName"];
    NSString* callId = hasId?incomingCall[@"callId"]:callName;
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall: INCOMING callId:'%@'", callId);
    
    
    NSUUID *callUUID = [[NSUUID alloc] init];
    
    if (hasId) {
        [[NSUserDefaults standardUserDefaults] setObject:callName forKey:callId];
        [[NSUserDefaults standardUserDefaults] synchronize];
    }
    
    if (callName != nil && [callName length] > 0) {
        CXHandle *handle = [[CXHandle alloc] initWithType:CXHandleTypePhoneNumber value:callId];
        CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
        
        callUpdate.remoteHandle = handle; //<<CXHandle callId
        
        callUpdate.hasVideo = hasVideo;
        callUpdate.localizedCallerName = callName;
        callUpdate.supportsGrouping = NO;
        callUpdate.supportsUngrouping = NO;
        callUpdate.supportsHolding = NO;
        callUpdate.supportsDTMF = enableDTMF;
        
        
        //------------------------------------------------------------------------------------------
        //CHINA
        if(self.provider){
            //--------------------------------------------------------------------------------------
            //SHOWS the ANSWER/DECLINE VOIP CALL ALERT
            //--------------------------------------------------------------------------------------
            [self.provider reportNewIncomingCallWithUUID:callUUID
                                                  update:callUpdate
                                              completion:^(NSError * _Nullable error)
            {
                if(error == nil) {
                    //------------------------------------------------------------------------------
                    //RETURNS IMMEDIATELY - the Answer/Decline ui should be showing and phone ringing
                    //if user presses ANSWER it comes out in DELEGATE performAnswerCallAction:
                    //if user presses DECLINE it comes out in DELEGATE performAnswerCallAction:
                    //------------------------------------------------------------------------------
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                             messageAsString:@"Incoming call successful"]
                                                callbackId:command.callbackId];
                    //------------------------------------------------------------------------------
                    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall: INCOMING callsMetadata setValue:incomingCall forKey:UUID:%@", [callUUID UUIDString]);
                    [callsMetadata setValue:incomingCall forKey:[callUUID UUIDString]];
                    //------------------------------------------------------------------------------
                    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall: RESPONSE 'receiveCall' payload:%@", incomingCall);
                    [self sendEvent:@"receiveCall" payload:incomingCall];
                } else {
                    //------------------------------------------------------------------------------
                    //ERROR
                    //------------------------------------------------------------------------------
                    [self logIncomingCallError: error];
                    
                    //------------------------------------------------------------------------------
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]] callbackId:command.callbackId];
                }
            }];
            //----------------------------------------------------------------------------------
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall: self.provider is NULL");
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"provider is nil"] callbackId:command.callbackId];
        }
        //------------------------------------------------------------------------------------------
        
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Caller id can't be empty"] callbackId:command.callbackId];
    }
}

-(void) logIncomingCallError:(NSError *) error{
    
    //----------------------------------------------------------------------------------------------
    //https://developer.apple.com/documentation/callkit/cxerrorcodeincomingcallerror?language=objc
    //----------------------------------------------------------------------------------------------
    //    CXErrorCodeIncomingCallErrorUnknown
    //    An unknown error occurred.
    
    //    CXErrorCodeIncomingCallErrorUnentitled
    //    The app isn’t entitled to receive incoming calls.
    
    //    CXErrorCodeIncomingCallErrorCallUUIDAlreadyExists
    //    The incoming call UUID already exists.
    
    //    CXErrorCodeIncomingCallErrorFilteredByDoNotDisturb
    //    The incoming call is filtered because Do Not Disturb is active and the incoming caller is not a VIP.
    
    //    CXErrorCodeIncomingCallErrorFilteredByBlockList
    //    The incoming call is filtered because the incoming caller has been blocked by the user.
    //----------------------------------------------------------------------------------------------
    
    NSInteger errorCode = [error code];
    if(CXErrorCodeIncomingCallErrorUnknown == errorCode){
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [CXErrorCodeIncomingCallErrorUnknown]", error);
        
    }else if(CXErrorCodeIncomingCallErrorUnentitled == errorCode){
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [CXErrorCodeIncomingCallErrorUnentitled]", error);
        
    }else if(CXErrorCodeIncomingCallErrorCallUUIDAlreadyExists == errorCode){
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [CXErrorCodeIncomingCallErrorCallUUIDAlreadyExists]", error);
        
    }else if(CXErrorCodeIncomingCallErrorFilteredByDoNotDisturb == errorCode){
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [CXErrorCodeIncomingCallErrorFilteredByDoNotDisturb]", error);
        
    }else if(CXErrorCodeIncomingCallErrorFilteredByBlockList == errorCode){
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [CXErrorCodeIncomingCallErrorFilteredByBlockList]", error);
        
    }else {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] receiveCall:  >> reportNewIncomingCallWithUUID FAILED error:%@ [UNHANDLED]", error);
    }
}

#pragma mark -
#pragma mark sendCall
#pragma mark -
- (void)sendCall:(CDVInvokedUrlCommand*)command
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] sendCall: CALLED");
    
    NSDictionary *outgoingCall = [command.arguments objectAtIndex:0];
    if(outgoingCall == nil) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Call is not defined"] callbackId:command.callbackId];
    }
    BOOL hasId = ![outgoingCall[@"callId"] isEqual:[NSNull null]];
    NSString* callName = outgoingCall[@"callName"];
    NSString* callId = hasId?outgoingCall[@"callId"]:callName;
    NSUUID *callUUID = [[NSUUID alloc] init];
    
    if (hasId) {
        [[NSUserDefaults standardUserDefaults] setObject:callName forKey:callId];
        [[NSUserDefaults standardUserDefaults] synchronize];
    }
    
    if (callName != nil && [callName length] > 0) {
        CXHandle *handle = [[CXHandle alloc] initWithType:CXHandleTypePhoneNumber value:callId];
        CXStartCallAction *startCallAction = [[CXStartCallAction alloc] initWithCallUUID:callUUID handle:handle];
        startCallAction.contactIdentifier = callName;
        startCallAction.video = hasVideo;
        CXTransaction *transaction = [[CXTransaction alloc] initWithAction:startCallAction];
        [self.callController requestTransaction:transaction completion:^(NSError * _Nullable error) {
            if (error == nil) {
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Outgoing call successful"] callbackId:command.callbackId];
                
                NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] sendCall: callsMetadata setValue:outgoingCall forKey:UUID:%@", [callUUID UUIDString]);
                [callsMetadata setValue:outgoingCall forKey:[callUUID UUIDString]];
            } else {
                [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]] callbackId:command.callbackId];
            }
        }];
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"The caller id can't be empty"] callbackId:command.callbackId];
    }
}

- (void)connectCall:(CDVInvokedUrlCommand*)command
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] connectCall: CALLED");
    
    CDVPluginResult* pluginResult = nil;
    NSArray<CXCall *> *calls = self.callController.callObserver.calls;
    
    if([calls count] == 1) {
        
        //CHINA
        if(self.provider){
            //--------------------------------------------------------------------------------------
            [self.provider reportOutgoingCallWithUUID:calls[0].UUID connectedAtDate:nil];
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Call connected successfully"];
            //--------------------------------------------------------------------------------------
        }else{
            NSLog(@"self.provider is NULL - [self.provider reportOutgoingCallWithUUID:...] FAILED - is user in CHINA/CN/CHN");
            
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"provider is null"];
        }
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"No call exists for you to connect"];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark -
#pragma mark END CALL - endCall
#pragma mark -
- (void)endCall:(CDVInvokedUrlCommand*)command
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] endCall: START ********");
    CDVPluginResult* pluginResult = nil;
    NSArray<CXCall *> *calls = self.callController.callObserver.calls;
    
    if([calls count] == 1) {
        //[self.provider reportCallWithUUID:calls[0].UUID endedAtDate:nil reason:CXCallEndedReasonRemoteEnded];
        CXEndCallAction *endCallAction = [[CXEndCallAction alloc] initWithCallUUID:calls[0].UUID];
        CXTransaction *transaction = [[CXTransaction alloc] initWithAction:endCallAction];
        [self.callController requestTransaction:transaction completion:^(NSError * _Nullable error) {
            if (error == nil) {
            } else {
                NSLog(@"%@",[error localizedDescription]);
            }
        }];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Call ended successfully"];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"No call exists for you to connect"];
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)requestMicPermission:(CDVInvokedUrlCommand*)command
{
    __block CDVPluginResult* pluginResult = nil;
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
    switch (status) {
        case AVAuthorizationStatusAuthorized:
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Record permission has been granted"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        break;
        case AVAuthorizationStatusDenied:
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Record permission has not been granted"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        break;
        case AVAuthorizationStatusNotDetermined:
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeAudio completionHandler: ^ (BOOL granted)
            {
                if (granted) {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Record permission has been granted"];
                } else {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Record permission has not been granted"];
            }
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }];
        break;
    }
}

- (void)requestCameraPermission:(CDVInvokedUrlCommand*)command
{
    __block CDVPluginResult* pluginResult = nil;
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    switch (status) {
        case AVAuthorizationStatusAuthorized:
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Camera permission has been granted"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        break;
        case AVAuthorizationStatusDenied:
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Camera permission has not been granted"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        break;
        case AVAuthorizationStatusNotDetermined:
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler: ^ (BOOL granted)
            {
            dispatch_async(dispatch_get_main_queue(), ^{
                    if (granted) {
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Camera permission has been granted"];
                    } else {
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Camera permission has not been granted"];
                }
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            });
        }];
        break;
    }
}


- (void)mute:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
    if(sessionInstance.isInputGainSettable) {
        BOOL success = [sessionInstance setInputGain:0.0 error:nil];
        if(success) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Muted Successfully"];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"An error occurred"];
        }
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Not muted because this device does not allow changing inputGain"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)unmute:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
    if(sessionInstance.isInputGainSettable) {
        BOOL success = [sessionInstance setInputGain:1.0 error:nil];
        if(success) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Muted Successfully"];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"An error occurred"];
        }
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Not unmuted because this device does not allow changing inputGain"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)speakerOn:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] speakerOn: overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker");
    
    BOOL success = [sessionInstance overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:nil];
    if(success) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Speakerphone is on"];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"An error occurred"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)speakerOff:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] speakerOff: overrideOutputAudioPort:AVAudioSessionPortOverrideNone");
    
    BOOL success = [sessionInstance overrideOutputAudioPort:AVAudioSessionPortOverrideNone error:nil];
    if(success) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Speakerphone is off"];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"An error occurred"];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)callNumber:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* phoneNumber = [command.arguments objectAtIndex:0];
    NSString* telNumber = [@"tel://" stringByAppendingString:phoneNumber];
    if (@available(iOS 10.0, *)) {
        [[UIApplication sharedApplication] openURL:[NSURL URLWithString:telNumber]
                                           options:nil
                                 completionHandler:^(BOOL success) {
            if(success) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Call Successful"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            } else {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Call Failed"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        }];
    } else {
        BOOL success = [[UIApplication sharedApplication] openURL:[NSURL URLWithString:telNumber]];
        if(success) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Call Successful"];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Call Failed"];
        }
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    
}

- (void)reportCallEndedReason:(CDVInvokedUrlCommand *)command
{
    NSString* callId = [command.arguments objectAtIndex:0];
    NSString* reason = [command.arguments objectAtIndex:1];
    if ([reason isEqualToString:@"CallAnsweredElsewhere"]) {
        NSUUID* callUUID = [self getCallUUID:callId];
        if(callUUID != nil) {
            NSArray<CXCall *> *calls = self.callController.callObserver.calls;
            if([calls count] == 1 && [calls[0].UUID isEqual:callUUID] && !calls[0].hasConnected && !calls[0].hasEnded) {
                [self.provider reportCallWithUUID:calls[0].UUID endedAtDate:[[NSDate alloc] init] reason:CXCallEndedReasonAnsweredElsewhere];
            }
        }
    } else if ([reason isEqualToString:@"CallDeclinedElsewhere"]) {
        NSUUID* callUUID = [self getCallUUID:callId];
        if(callUUID != nil) {
            NSArray<CXCall *> *calls = self.callController.callObserver.calls;
            if([calls count] == 1 && [calls[0].UUID isEqual:callUUID] && !calls[0].hasEnded) {
                [self.provider reportCallWithUUID:calls[0].UUID endedAtDate:[[NSDate alloc] init] reason:CXCallEndedReasonDeclinedElsewhere];
            }
        }
    } else if ([reason isEqualToString:@"CallMissed"]) {
        NSUUID* callUUID = [self getCallUUID:callId];
        if(callUUID != nil) {
            NSArray<CXCall *> *calls = self.callController.callObserver.calls;
            if([calls count] == 1 && [calls[0].UUID isEqual:callUUID] && !calls[0].hasEnded) {
                [self.provider reportCallWithUUID:calls[0].UUID endedAtDate:[[NSDate alloc] init] reason:CXCallEndedReasonUnanswered];
            }
        }
    } else if ([reason isEqualToString:@"CallCompleted"]) {
        NSUUID* callUUID = [self getCallUUID:callId];
        if(callUUID != nil) {
            NSArray<CXCall *> *calls = self.callController.callObserver.calls;
            if([calls count] == 1 && [calls[0].UUID isEqual:callUUID] && !calls[0].hasEnded) {
                [self.provider reportCallWithUUID:calls[0].UUID endedAtDate:[[NSDate alloc] init] reason:CXCallEndedReasonRemoteEnded];
            }
        }
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)receiveCallFromRecents:(NSNotification *) notification
{
    NSString* callID = notification.object[@"callId"];
    NSString* callName = notification.object[@"callName"];
    NSUUID *callUUID = [[NSUUID alloc] init];
    CXHandle *handle = [[CXHandle alloc] initWithType:CXHandleTypePhoneNumber value:callID];
    CXStartCallAction *startCallAction = [[CXStartCallAction alloc] initWithCallUUID:callUUID handle:handle];
    startCallAction.video = [notification.object[@"isVideo"] boolValue]?YES:NO;
    startCallAction.contactIdentifier = callName;
    CXTransaction *transaction = [[CXTransaction alloc] initWithAction:startCallAction];
    [self.callController requestTransaction:transaction completion:^(NSError * _Nullable error) {
        if (error == nil) {
        } else {
            NSLog(@"%@",[error localizedDescription]);
        }
    }];
}

- (void)handleAudioRouteChange:(NSNotification *) notification
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: START ********");
    
    //NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: notification:\r%@", notification

    if(NULL != notification){
        //------------------------------------------------------------------------------------------
        //Name
        //------------------------------------------------------------------------------------------
        if(NULL != notification.name){
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: notification.name:%@", notification.name);
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: notification.name is NULL");
        }
        
        //------------------------------------------------------------------------------------------
        //RouteChangeReason
        //------------------------------------------------------------------------------------------
        NSNumber* reasonValueNumber = notification.userInfo[@"AVAudioSessionRouteChangeReasonKey"];
        if(NULL != reasonValueNumber){
            NSString * reasonString = [self stringForAVAudioSessionRouteChangeReason:[reasonValueNumber intValue]];
            
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: RouteChangeReason: %@", reasonString);
            
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: RouteChangeReason: reasonValueNumber is NULL");
        }

        //----------------------------------------------------------------------------------------
        //PreviousRoute
        //----------------------------------------------------------------------------------------
        AVAudioSessionRouteDescription* previousRoute = notification.userInfo[@"AVAudioSessionRouteChangePreviousRouteKey"];
        if(NULL != previousRoute){
            //--------------------------------------------------------------------------------------
            NSArray* inputs = [previousRoute inputs];
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: PREVIOUS: INPUTS: [inputs count]: %ld", [inputs count]);
            
            for (AVAudioSessionPortDescription *input in inputs) {
                NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: PREVIOUS: INPUT: %@", input);
            }
            
            //--------------------------------------------------------------------------------------
            //PREVIOUS - OUTPUTS
            //--------------------------------------------------------------------------------------
            NSArray* outputs = [previousRoute outputs];
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: PREVIOUS: OUTPUTS: [outputs count]: %ld", [outputs count]);
            
            for (AVAudioSessionPortDescription *output in outputs) {
                NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: PREVIOUS: OUTPUT: %@", output);
            }
            //--------------------------------------------------------------------------------------
            
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: reasonValueNumberis NULL");
        }

        //------------------------------------------------------------------------------------------
        //CURRENT
        //------------------------------------------------------------------------------------------
        if(NULL != [AVAudioSession sharedInstance]){
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: CURRENT: [AVAudioSession sharedInstance].category:%@", [AVAudioSession sharedInstance].category);
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: CURRENT: [AVAudioSession sharedInstance].mode:%@", [AVAudioSession sharedInstance].mode);
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: CURRENT: [AVAudioSession sharedInstance].outputVolume:%f", [AVAudioSession sharedInstance].outputVolume);
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: CURRENT: [AVAudioSession sharedInstance].currentRoute:\r%@", [AVAudioSession sharedInstance].currentRoute);
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: CURRENT: [AVAudioSession sharedInstance] is NULL");
        }
        
        //------------------------------------------------------------------------------------------
    }else{
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] handleAudioRouteChange: handleAudioRouteChange is NULL");
    }
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m][AUDIO] handleAudioRouteChange: END ********");

}


- (NSString *) stringForAVAudioSessionRouteChangeReason:(int) reasonValue{
    NSString * reason = @"ERROR_UNHANDLED_RouteChangeReason";

    
    //----------------------------------------------------------------------------------------------
    //    typedef NS_ENUM(NSUInteger, AVAudioSessionRouteChangeReason) {
    //        /// The reason is unknown.
    //        AVAudioSessionRouteChangeReasonUnknown = 0,
    //
    //        /// A new device became available (e.g. headphones have been plugged in).
    //        AVAudioSessionRouteChangeReasonNewDeviceAvailable = 1,
    //
    //        /// The old device became unavailable (e.g. headphones have been unplugged).
    //        AVAudioSessionRouteChangeReasonOldDeviceUnavailable = 2,
    //
    //        /// The audio category has changed (e.g. AVAudioSessionCategoryPlayback has been changed to
    //        /// AVAudioSessionCategoryPlayAndRecord).
    //        AVAudioSessionRouteChangeReasonCategoryChange = 3,
    //
    //        /// The route has been overridden (e.g. category is AVAudioSessionCategoryPlayAndRecord and
    //        /// the output has been changed from the receiver, which is the default, to the speaker).
    //        AVAudioSessionRouteChangeReasonOverride = 4,
    //
    //        /// The device woke from sleep.
    //        AVAudioSessionRouteChangeReasonWakeFromSleep = 6,
    //
    //        /// Returned when there is no route for the current category (for instance, the category is
    //        /// AVAudioSessionCategoryRecord but no input device is available).
    //        AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory = 7,
    //
    //        /// Indicates that the set of input and/our output ports has not changed, but some aspect of
    //        /// their configuration has changed.  For example, a port's selected data source has changed.
    //        /// (Introduced in iOS 7.0, watchOS 2.0, tvOS 9.0).
    //        AVAudioSessionRouteChangeReasonRouteConfigurationChange = 8
    //    };
    //----------------------------------------------------------------------------------------------
    
    if(AVAudioSessionRouteChangeReasonUnknown == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonUnknown - REASON UNKNOWN";
        
    }
    else if(AVAudioSessionRouteChangeReasonNewDeviceAvailable == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonNewDeviceAvailable - REASON - NEW DEVICE AVAILABLE";
        
    }
    else if(AVAudioSessionRouteChangeReasonOldDeviceUnavailable == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonOldDeviceUnavailable - REASON - OLD DEVICE UNAVAILABLE";
        
    }
    else if(AVAudioSessionRouteChangeReasonCategoryChange == reasonValue){

        reason = [NSString stringWithFormat:@"AVAudioSessionRouteChangeReasonCategoryChange - REASON - CATEGORY CHANGE TO:%@", [AVAudioSession sharedInstance].category];
        
    }
    else if(AVAudioSessionRouteChangeReasonOverride == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonOverride - REASON - REASON OVERRIDE";
        
    }
    else if(AVAudioSessionRouteChangeReasonWakeFromSleep == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonWakeFromSleep - REASON - WAKE FROM SLEEP";
        
    }
    else if(AVAudioSessionRouteChangeReasonRouteConfigurationChange == reasonValue){
        reason = @"AVAudioSessionRouteChangeReasonRouteConfigurationChange - REASON - CONFIGURATION CHANGE";
        
    }
    else {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] stringForAVAudioSessionRouteChangeReason: UNHANDLED reasonValue:%d", reasonValue);
    }
    
    return reason;
}





#pragma mark -
#pragma mark CallKit - Provider
#pragma mark -

- (void)providerDidReset:(CXProvider *)provider
{
    NSLog(@"%s","providerdidreset");
}

#pragma mark -
#pragma mark performStartCallAction
#pragma mark -
- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action
{
    //FOR INCOMING CALL THIS TURNS ON THE SPEAKER / for outgoing its TwilioSDK.audioDevice....in TVA
    //may be interfering with mp3
    //AVSession configured in TVC > viewDidLoad > audioDevice.block
    [self setupAudioSession];
    
    
    CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
    callUpdate.remoteHandle = action.handle;
    callUpdate.hasVideo = action.video;
    callUpdate.localizedCallerName = action.contactIdentifier;
    callUpdate.supportsGrouping = NO;
    callUpdate.supportsUngrouping = NO;
    callUpdate.supportsHolding = NO;
    callUpdate.supportsDTMF = enableDTMF;
    
    [self.provider reportCallWithUUID:action.callUUID updated:callUpdate];
    [action fulfill];
    NSDictionary *data  = callsMetadata[[action.callUUID UUIDString]];
    if(data == nil) {
        return;
    }
    [self sendEvent:@"sendCall" payload:data];
}

- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] provider:didActivateAudioSession:");
    
    monitorAudioRouteChange = YES;
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] didDeactivateAudioSession: deactivated audio");
}

#pragma mark -
#pragma mark performAnswerCallAction - USER PRESSES ANSWER on INCOMING CALL
#pragma mark -

- (void)provider:(CXProvider *)provider performAnswerCallAction:(CXAnswerCallAction *)action
{
    [self setupAudioSession];
    [action fulfill];
    NSDictionary *call = callsMetadata[[action.callUUID UUIDString]];
    if(call == nil) {
        return;
    }
    [self sendEvent:@"answer" payload:call];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action
{
    NSArray<CXCall *> *calls = self.callController.callObserver.calls;
    NSDictionary *call = callsMetadata[[action.callUUID UUIDString]];
    if([calls count] == 1 && call != nil) {
        if(calls[0].hasConnected) {
            NSDictionary *payload = @{@"callId":call[@"callId"], @"callName": call[@"callName"]};
            [self sendEvent:@"hangup" payload:payload];
        } else {
            [self sendEvent:@"reject" payload:call];
        }
        [callsMetadata removeObjectForKey:[action.callUUID UUIDString]];
    }
    monitorAudioRouteChange = NO;
    [action fulfill];
}

- (void)provider:(CXProvider *)provider performSetMutedCallAction:(CXSetMutedCallAction *)action
{
    [action fulfill];
    BOOL isMuted = action.muted;
    [self sendEvent:isMuted?@"mute":@"unmute" payload:@{}];
}

- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action
{
    NSLog(@"DTMF Event");
    NSString *digits = action.digits;
    NSDictionary *payload = @{@"digits":digits};
    [action fulfill];
    [self sendEvent:@"DTMF" payload:payload];
}

- (void)sendEvent:(NSString*)eventName payload:(NSDictionary*)payload
{
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] RESPONSE START *************************");
    if(eventCallbackId == nil) {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] RESPONSE >> sendEvent: ERROR eventCallbackId == nil > return");
        return;
    }
    
    NSDictionary *event = @{@"eventName":eventName, @"data":payload};
    
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] RESPONSE >> sendEvent:event:'%@'", event);
    NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] RESPONSE END  ***************************");
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:event];
    [pluginResult setKeepCallbackAsBool:YES];
    
    //NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] sendEvent: pluginResult:'%@'", pluginResult);
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:eventCallbackId];
}

// PushKit
- (void)initVoip:(CDVInvokedUrlCommand*)command
{
    if ([self isCallKitDisabledForChina]) {
        return;
    }

    self.VoIPPushCallbackId = command.callbackId;
    NSLog(@"[objC] callbackId: %@", self.VoIPPushCallbackId);
    //http://stackoverflow.com/questions/27245808/implement-pushkit-and-test-in-development-behavior/28562124#28562124
    PKPushRegistry *pushRegistry = [[PKPushRegistry alloc] initWithQueue:dispatch_get_main_queue()];
    pushRegistry.delegate = self;
    pushRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
}

- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)credentials forType:(NSString *)type{
    if([credentials.token length] == 0) {
        NSLog(@"[objC] No device token!");
        return;
    }
    
    //http://stackoverflow.com/a/9372848/534755
    NSLog(@"[objC] Device token: %@", credentials.token);
    const unsigned *tokenBytes = [credentials.token bytes];
    NSString *sToken = [NSString stringWithFormat:@"%08x%08x%08x%08x%08x%08x%08x%08x",
                        ntohl(tokenBytes[0]), ntohl(tokenBytes[1]), ntohl(tokenBytes[2]),
                        ntohl(tokenBytes[3]), ntohl(tokenBytes[4]), ntohl(tokenBytes[5]),
                        ntohl(tokenBytes[6]), ntohl(tokenBytes[7])];
    
    NSMutableDictionary* results = [NSMutableDictionary dictionaryWithCapacity:2];
    [results setObject:sToken forKey:@"deviceToken"];
    [results setObject:@"true" forKey:@"registration"];
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:results];
    [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]]; //[pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.VoIPPushCallbackId];
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(NSString *)type
{
    NSDictionary *payloadDict = payload.dictionaryPayload[@"aps"];
    NSLog(@"[objC] didReceiveIncomingPushWithPayload: %@", payloadDict);
    
    NSString *message = payloadDict[@"alert"];
    NSLog(@"[objC] received VoIP message: %@", message);
    
    NSDictionary *data = payload.dictionaryPayload[@"data"];
    NSLog(@"[objC] received data: %@", data);
    
    NSMutableDictionary* results = [NSMutableDictionary dictionaryWithCapacity:2];
    [results setObject:message forKey:@"function"];
    [results setObject:data forKey:@"extra"];
    
    @try {
        NSDictionary *content = data[@"content"];
        NSArray* args = [NSArray arrayWithObjects:content,nil];
        CDVInvokedUrlCommand* newCommand = [[CDVInvokedUrlCommand alloc] initWithArguments:args callbackId:@"" className:self.VoIPPushClassName methodName:self.VoIPPushMethodName];
        [self receiveCall:newCommand];
    }
    @catch (NSException *exception) {
       NSLog(@"[objC] error: %@", exception.reason);
    }
    @finally {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:results];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.VoIPPushCallbackId];
    }
}

- (NSUUID*)getCallUUID:(NSString*)callId
{
    __block NSString* callUUIDString = nil;
    [callsMetadata enumerateKeysAndObjectsUsingBlock:^(NSString*  _Nonnull key, NSDictionary*  _Nonnull obj, BOOL * _Nonnull stop) {
        if([obj[@"callId"] isEqualToString:callId]) {
            callUUIDString = key;
            *stop = YES;
        }
    }];
    return callUUIDString != nil ? [[NSUUID alloc] initWithUUIDString:callUUIDString] : nil;
}

@end
