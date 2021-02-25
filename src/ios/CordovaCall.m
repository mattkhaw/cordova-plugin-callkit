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

- (BOOL)isCallKitDisabledForChina{
    BOOL isCallKitDisabledForChina = FALSE;
    
    NSLocale *currentLocale = [NSLocale currentLocale];
    
    NSLog(@"currentLocale.countryCode:'%@'", currentLocale);
    NSLog(@"currentLocale.localeIdentifier:'%@'", currentLocale.localeIdentifier);
    NSLog(@"currentLocale.countryCode:'%@'", currentLocale.countryCode);
    NSLog(@"currentLocale.languageCode:'%@'", currentLocale.languageCode);
    
    if ([currentLocale.countryCode containsString: @"CN"] || [currentLocale.countryCode containsString: @"CHN"]) {
        NSLog(@"currentLocale is China so we CANNOT use CallKit.");
        isCallKitDisabledForChina = TRUE;
        
    } else {
        NSLog(@"currentLocale is NOT China(CN/CHN) so we CAN use CallKit.");
        isCallKitDisabledForChina = FALSE;
    }
    
    return isCallKitDisabledForChina;
}

- (void)pluginInitialize
{
    //CALLKIT banned in china
    NSLocale *currentLocale = [NSLocale currentLocale];
    
    NSLog(@"currentLocale.countryCode:'%@'", currentLocale);
    NSLog(@"currentLocale.localeIdentifier:'%@'", currentLocale.localeIdentifier);
    NSLog(@"currentLocale.countryCode:'%@'", currentLocale.countryCode);
    NSLog(@"currentLocale.languageCode:'%@'", currentLocale.languageCode);
    
    
    
    
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
    
    
    
    
    self.callController = [[CXCallController alloc] init];
    callsMetadata = [[NSMutableDictionary alloc]initWithCapacity:5];
    
    
    //allows user to make call from recents
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(receiveCallFromRecents:) name:@"RecentsCallNotification" object:nil];
    //detect Audio Route Changes to make speakerOn and speakerOff event handlers
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleAudioRouteChange:) name:AVAudioSessionRouteChangeNotification object:nil];
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

- (void)setupAudioSession
{
    @try {
        AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
        //------------------------------------------------------------------------------------------
        [sessionInstance setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];

        //------------------------------------------------------------------------------------------
        //SPEAKERPHONE/RECEIVER(earpiece)
        //in original twilio sample but doesnt show Speaker when we tap on airplay picker
        /*! Only valid with AVAudioSessionCategoryPlayAndRecord.  Appropriate for Voice over IP
         (VoIP) applications.  Reduces the number of allowable audio routes to be only those
         that are appropriate for VoIP applications and may engage appropriate system-supplied
         signal processing.  Has the side effect of setting AVAudioSessionCategoryOptionAllowBluetooth */
        //WRONG - use AVAudioSessionModeVideoChat
        //[sessionInstance setMode:AVAudioSessionModeVoiceChat error:nil];
        //------------------------------------------------------------------------------------------

        /*! Only valid with kAudioSessionCategory_PlayAndRecord. Reduces the number of allowable audio
         routes to be only those that are appropriate for video chat applications. May engage appropriate
         system-supplied signal processing.  Has the side effect of setting
         AVAudioSessionCategoryOptionAllowBluetooth and AVAudioSessionCategoryOptionDefaultToSpeaker. */
        //SPEAKERPHONE - REQUIRED ELSE SPEAKER doesnt appear
        [sessionInstance setMode:AVAudioSessionModeVideoChat error:nil];
        //------------------------------------------------------------------------------------------
        
        
//        //https://github.com/iFLYOS-OPEN/SDK-EVS-iOS/blob/a111b7765fab62586be72199c417e2b103317e44/Pod/Classes/common/media_player/AudioSessionManager.m
//        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker|AVAudioSessionCategoryOptionMixWithOthers error:nil];
//        [[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:nil];
//        [[AVAudioSession sharedInstance] setActive:YES error:nil];
        
        
        
        NSTimeInterval bufferDuration = .005;
        [sessionInstance setPreferredIOBufferDuration:bufferDuration error:nil];
        [sessionInstance setPreferredSampleRate:44100 error:nil];
        NSLog(@"Configuring Audio");
    }
    @catch (NSException *exception) {
        NSLog(@"Unknown error returned from setupAudioSession");
    }
    return;
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
    CDVPluginResult* pluginResult = nil;
    hasVideo = [[command.arguments objectAtIndex:0] boolValue];
    [self updateProviderConfig];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"hasVideo Changed Successfully"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)receiveCall:(CDVInvokedUrlCommand*)command
{
    NSDictionary *incomingCall = [command.arguments objectAtIndex:0];
    if (incomingCall == nil) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Call is not defined"] callbackId:command.callbackId];
    }
    
    BOOL hasId = ![incomingCall[@"callId"] isEqual:[NSNull null]];
    NSString* callName = incomingCall[@"callName"];
    NSString* callId = hasId?incomingCall[@"callId"]:callName;
    NSUUID *callUUID = [[NSUUID alloc] init];
    
    if (hasId) {
        [[NSUserDefaults standardUserDefaults] setObject:callName forKey:callId];
        [[NSUserDefaults standardUserDefaults] synchronize];
    }
    
    if (callName != nil && [callName length] > 0) {
        CXHandle *handle = [[CXHandle alloc] initWithType:CXHandleTypePhoneNumber value:callId];
        CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
        callUpdate.remoteHandle = handle;
        callUpdate.hasVideo = hasVideo;
        callUpdate.localizedCallerName = callName;
        callUpdate.supportsGrouping = NO;
        callUpdate.supportsUngrouping = NO;
        callUpdate.supportsHolding = NO;
        callUpdate.supportsDTMF = enableDTMF;
        
        
        //------------------------------------------------------------------------------------------
        //CHINA
        if(self.provider){
            //----------------------------------------------------------------------------------
            //SHOWS the ANSWER/DECLINE VOIP CALL ALERT
            [self.provider reportNewIncomingCallWithUUID:callUUID update:callUpdate completion:^(NSError * _Nullable error) {
                if(error == nil) {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Incoming call successful"] callbackId:command.callbackId];
                    [callsMetadata setValue:incomingCall forKey:[callUUID UUIDString]];
                    [self sendEvent:@"receiveCall" payload:incomingCall];
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]] callbackId:command.callbackId];
                }
            }];
            //----------------------------------------------------------------------------------
        }else{
            NSLog(@"self.provider is NULL");
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"provider is nil"] callbackId:command.callbackId];
        }
        //------------------------------------------------------------------------------------------
        
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Caller id can't be empty"] callbackId:command.callbackId];
    }
}

- (void)sendCall:(CDVInvokedUrlCommand*)command
{
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

- (void)endCall:(CDVInvokedUrlCommand*)command
{
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
    if(monitorAudioRouteChange) {
        NSNumber* reasonValue = notification.userInfo[@"AVAudioSessionRouteChangeReasonKey"];
        AVAudioSessionRouteDescription* previousRouteKey = notification.userInfo[@"AVAudioSessionRouteChangePreviousRouteKey"];
        NSArray* outputs = [previousRouteKey outputs];
        if([outputs count] > 0) {
            AVAudioSessionPortDescription *output = outputs[0];
            
            //--------------------------------------------------------------------------------------
            //SPEAKERPHONE
            //--------------------------------------------------------------------------------------
            //BC - if you change from Speaker to iPhone in the AirPLay picker this tell cordova
            //'Speaker' > speakerOn     - AVAudioSessionPortBuiltInSpeaker constant maps to string 'Speaker'
            //NOT'Speaker' > speakerOff - 'Receiver' //AVAudioSessionPortBuiltInReceiver
            //--------------------------------------------------------------------------------------
            if(![output.portType isEqual: @"Speaker"] && [reasonValue isEqual:@4]) {
                [self sendEvent:@"speakerOn" payload:@{}];
                
            } else if([output.portType isEqual: @"Speaker"] && [reasonValue isEqual:@3]) {
                [self sendEvent:@"speakerOff" payload:@{}];
                
            }
        }
    }
}

// CallKit - Provider
- (void)providerDidReset:(CXProvider *)provider
{
    NSLog(@"%s","providerdidreset");
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action
{
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
    NSLog(@"activated audio");
    monitorAudioRouteChange = YES;
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession
{
    NSLog(@"deactivated audio");
}

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
    if(eventCallbackId == nil) {
        return;
    }
    
    NSDictionary *event = @{@"eventName":eventName, @"data":payload};
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:event];
    [pluginResult setKeepCallbackAsBool:YES];
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
