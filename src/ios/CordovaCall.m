#import "CordovaCall.h"
#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>

@implementation CordovaCall

@synthesize VoIPPushCallbackId, VoIPPushClassName, VoIPPushMethodName;

BOOL hasVideo = YES;
NSString* appName;
NSString* ringtone;
NSString* icon;
BOOL includeInRecents = NO;
NSMutableDictionary<NSString*, NSMutableArray*> *callbackIds;
NSDictionary* pendingCallFromRecents;
BOOL monitorAudioRouteChange = NO;
BOOL enableDTMF = NO;
PKPushRegistry *_voipRegistry;

BOOL isCancelPush = NO;
NSString* callBackUrl;
NSString* callId;

NSMutableArray* pendingCallResponses;
NSString* const PENDING_RESPONSE_ANSWER = @"pendingResponseAnswer";
NSString* const PENDING_RESPONSE_REJECT = @"pendingResponseReject";

NSString* const KEY_VOIP_PUSH_TOKEN = @"PK_deviceToken";

- (void)pluginInitialize
{
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
    self.provider = [[CXProvider alloc] initWithConfiguration:providerConfiguration];
    [self.provider setDelegate:self queue:nil];
    self.callController = [[CXCallController alloc] init];
    //initialize callback dictionary
    callbackIds = [[NSMutableDictionary alloc]initWithCapacity:5];
    [callbackIds setObject:[NSMutableArray array] forKey:@"answer"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"reject"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"hangup"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"sendCall"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"receiveCall"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"mute"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"unmute"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"speakerOn"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"speakerOff"];
    [callbackIds setObject:[NSMutableArray array] forKey:@"DTMF"];
    
    // Add call response (answer or reject) to pending if event listeners are not added at the time of responding
    pendingCallResponses = [NSMutableArray new];
    
    //allows user to make call from recents
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(receiveCallFromRecents:) name:@"RecentsCallNotification" object:nil];
    //detect Audio Route Changes to make speakerOn and speakerOff event handlers
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleAudioRouteChange:) name:AVAudioSessionRouteChangeNotification object:nil];
    
    // Initialize PKPushRegistry
    //http://stackoverflow.com/questions/27245808/implement-pushkit-and-test-in-development-behavior/28562124#28562124
    dispatch_queue_t mainQueue = dispatch_get_main_queue();
    // Create a push registry object
    _voipRegistry = [[PKPushRegistry alloc] initWithQueue: mainQueue];
    // Set the registry's delegate to self
    [_voipRegistry setDelegate:(id<PKPushRegistryDelegate> _Nullable)self];
    // Set the push type to VoIP
    _voipRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
    
    // Read VoIPPushToken from UserDefaults
    self.VoIPPushToken = [[NSUserDefaults standardUserDefaults] stringForKey:KEY_VOIP_PUSH_TOKEN];
}

// CallKit - Interface
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

    self.provider.configuration = providerConfiguration;
}

- (void)setupAudioSession
{
    @try {
      AVAudioSession *sessionInstance = [AVAudioSession sharedInstance];
      [sessionInstance setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
      [sessionInstance setMode:AVAudioSessionModeVoiceChat error:nil];
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
    BOOL hasId = ![[command.arguments objectAtIndex:1] isEqual:[NSNull null]];
    NSString* callName = [command.arguments objectAtIndex:0];
    NSString* callId = hasId?[command.arguments objectAtIndex:1]:callName;
    NSUUID *callUUID = [[NSUUID alloc] init];

    if (hasId) {
        [[NSUserDefaults standardUserDefaults] setObject:callName forKey:[command.arguments objectAtIndex:1]];
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
        if (!isCancelPush) {
            [self.provider reportNewIncomingCallWithUUID:callUUID update:callUpdate completion:^(NSError * _Nullable error) {
                if(error == nil) {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Incoming call successful"] callbackId:command.callbackId];
                } else {
                    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]] callbackId:command.callbackId];
                }
            }];
            for (id callbackId in callbackIds[@"receiveCall"]) {
                CDVPluginResult* pluginResult = nil;
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"receiveCall event called successfully"];
                [pluginResult setKeepCallbackAsBool:YES];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
            }
        } else {
            NSArray<CXCall *> *calls = self.callController.callObserver.calls;
            if([calls count] == 1) {
                [self.provider reportCallWithUUID:calls[0].UUID endedAtDate:nil reason:CXCallEndedReasonRemoteEnded];
            }
            
        }
    } else {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Caller id can't be empty"] callbackId:command.callbackId];
    }
}

- (void)sendCall:(CDVInvokedUrlCommand*)command
{
    BOOL hasId = ![[command.arguments objectAtIndex:1] isEqual:[NSNull null]];
    NSString* callName = [command.arguments objectAtIndex:0];
    NSString* callId = hasId?[command.arguments objectAtIndex:1]:callName;
    NSUUID *callUUID = [[NSUUID alloc] init];

    if (hasId) {
        [[NSUserDefaults standardUserDefaults] setObject:callName forKey:[command.arguments objectAtIndex:1]];
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
        [self.provider reportOutgoingCallWithUUID:calls[0].UUID connectedAtDate:nil];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Call connected successfully"];
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

- (void)registerEvent:(CDVInvokedUrlCommand*)command
{
    NSString* eventName = [command.arguments objectAtIndex:0];
    if(callbackIds[eventName] != nil) {
        [callbackIds[eventName] addObject:command.callbackId];
    }
    if(pendingCallFromRecents && [eventName isEqual:@"sendCall"]) {
        NSDictionary *callData = pendingCallFromRecents;
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:callData];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    
    // In case of registerEvent answer or reject called after responding to call, trigger cordova event for the appropriate answer
    if ([eventName isEqualToString:@"answer"] && [pendingCallResponses containsObject:PENDING_RESPONSE_ANSWER]) {
        [self triggerCordovaEventForCallResponse:@"answer"];
        [pendingCallResponses removeObject:PENDING_RESPONSE_ANSWER];
    }
    if ([eventName isEqualToString:@"reject"] && [pendingCallResponses containsObject:PENDING_RESPONSE_REJECT]) {
        [self triggerCordovaEventForCallResponse:@"reject"];
        [pendingCallResponses removeObject:PENDING_RESPONSE_REJECT];
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
            if(![output.portType isEqual: @"Speaker"] && [reasonValue isEqual:@4]) {
                for (id callbackId in callbackIds[@"speakerOn"]) {
                    CDVPluginResult* pluginResult = nil;
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"speakerOn event called successfully"];
                    [pluginResult setKeepCallbackAsBool:YES];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
                }
            } else if([output.portType isEqual: @"Speaker"] && [reasonValue isEqual:@3]) {
                for (id callbackId in callbackIds[@"speakerOff"]) {
                    CDVPluginResult* pluginResult = nil;
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"speakerOff event called successfully"];
                    [pluginResult setKeepCallbackAsBool:YES];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
                }
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
    NSDictionary *callData = @{@"callName":action.contactIdentifier, @"callId": action.handle.value, @"isVideo": action.video?@YES:@NO, @"message": @"sendCall event called successfully"};
    for (id callbackId in callbackIds[@"sendCall"]) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:callData];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
    }
    if([callbackIds[@"sendCall"] count] == 0) {
        pendingCallFromRecents = callData;
    }
    //[action fail];
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

    // Notify Webhook that Native Call has been Answered
    NSURL *statusUpdateUrl = [NSURL URLWithString:[NSString stringWithFormat:@"%@?id=%@&input=%@", callBackUrl, callId, @"pickup"]];
    NSURLSession *session = [NSURLSession sharedSession];
    [[session dataTaskWithURL:statusUpdateUrl
              completionHandler:^(NSData *statusUpdateData,
                                  NSURLResponse *statusUpdateResponse,
                                  NSError *statusUpdateError) {
                // handle response
    }] resume];

    if ([callbackIds[@"answer"] count] == 0) {
        // callbackId for event not registered, add to pending to trigger on registration
        [pendingCallResponses addObject:PENDING_RESPONSE_ANSWER];
    } else {
        [self triggerCordovaEventForCallResponse:@"answer"];
    }
    //[action fail];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action
{
    NSArray<CXCall *> *calls = self.callController.callObserver.calls;
    if([calls count] == 1) {
        if(calls[0].hasConnected) {
            for (id callbackId in callbackIds[@"hangup"]) {
                CDVPluginResult* pluginResult = nil;
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"hangup event called successfully"];
                [pluginResult setKeepCallbackAsBool:YES];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
            }
        } else {
            // Notify Webhook that Native Call has been Declined
            if (!isCancelPush) {
                NSURL *statusUpdateUrl = [NSURL URLWithString:[NSString stringWithFormat:@"%@?id=%@&input=%@", callBackUrl, callId, @"declined_callee"]];
                NSURLSession *session = [NSURLSession sharedSession];
                [[session dataTaskWithURL:statusUpdateUrl
                        completionHandler:^(NSData *statusUpdateData,
                                            NSURLResponse *statusUpdateResponse,
                                            NSError *statusUpdateError) {
                            // handle response
                }] resume];
            }

            if ([callbackIds[@"reject"] count] == 0) {
                // callbackId for event not registered, add to pending to trigger on registration
                [pendingCallResponses addObject:PENDING_RESPONSE_REJECT];
            } else {
                [self triggerCordovaEventForCallResponse:@"reject"];
            }
        }
    }
    monitorAudioRouteChange = NO;
    [action fulfill];
    //[action fail];
}

- (void)triggerCordovaEventForCallResponse:(NSString*) response {
    if ([response isEqualToString:@"answer"]) {
        for (id callbackId in callbackIds[@"answer"]) {
            CDVPluginResult* pluginResult = nil;
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"answer event called successfully"];
            [pluginResult setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
        }
    } else if ([response isEqualToString:@"reject"]) {
        for (id callbackId in callbackIds[@"reject"]) {
            CDVPluginResult* pluginResult = nil;
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"reject event called successfully"];
            [pluginResult setKeepCallbackAsBool:YES];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
        }
    }
}

- (void)provider:(CXProvider *)provider performSetMutedCallAction:(CXSetMutedCallAction *)action
{
    [action fulfill];
    BOOL isMuted = action.muted;
    for (id callbackId in callbackIds[isMuted?@"mute":@"unmute"]) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:isMuted?@"mute event called successfully":@"unmute event called successfully"];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
    }
    //[action fail];
}

- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action
{
    NSLog(@"DTMF Event");
    NSString *digits = action.digits;
    [action fulfill];
    for (id callbackId in callbackIds[@"DTMF"]) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:digits];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
    }
}
// PushKit
- (void)init:(CDVInvokedUrlCommand*)command
{
    self.VoIPPushCallbackId = command.callbackId;
    NSLog(@"[objC] callbackId: %@", self.VoIPPushCallbackId);
    
    [self sendTokenPluginResult];
}

- (void)sendTokenPluginResult {
    if (!self.VoIPPushCallbackId || !self.VoIPPushToken) {
        return;
    }

    NSMutableDictionary* results = [NSMutableDictionary dictionaryWithCapacity:2];
    [results setObject:self.VoIPPushToken forKey:@"deviceToken"];
    [results setObject:@"true" forKey:@"registration"];
    
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:results];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.VoIPPushCallbackId];
}

#define PushKit Delegate Methods
- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)credentials forType:(PKPushType)type{
    if([credentials.token length] == 0) {
        NSLog(@"[objC] No device token!");
        return;
    }

    //http://stackoverflow.com/a/9372848/534755
    NSLog(@"[objC] Device token: %@", credentials.token);
    const unsigned *tokenBytes = [credentials.token bytes];
    self.VoIPPushToken = [NSString stringWithFormat:@"%08x%08x%08x%08x%08x%08x%08x%08x",
                         ntohl(tokenBytes[0]), ntohl(tokenBytes[1]), ntohl(tokenBytes[2]),
                         ntohl(tokenBytes[3]), ntohl(tokenBytes[4]), ntohl(tokenBytes[5]),
                         ntohl(tokenBytes[6]), ntohl(tokenBytes[7])];
    
    // Store VoIPPushToken in UserDefaults
    [[NSUserDefaults standardUserDefaults] setObject:self.VoIPPushToken forKey:KEY_VOIP_PUSH_TOKEN];
    
    [self sendTokenPluginResult];
}
- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type withCompletionHandler:(void (^)(void))completion
{
    NSDictionary *payloadDict = payload.dictionaryPayload[@"aps"];
    NSLog(@"[objC] didReceiveIncomingPushWithPayload: %@", payloadDict);

    NSString *message = payloadDict[@"alert"];
    NSLog(@"[objC] received VoIP message: %@", message);
    
    NSDictionary *data = payload.dictionaryPayload[@"data"];
    NSLog(@"[objC] received data: %@", data);
    
    NSMutableDictionary* results = [NSMutableDictionary dictionaryWithCapacity:2];
    [results setObject:message forKey:@"function"];
    [results setObject:@"" forKey:@"extra"];

    NSObject* caller = [data objectForKey:@"Caller"];
    NSArray* args = [NSArray arrayWithObjects:[caller valueForKey:@"Username"], [caller valueForKey:@"ConnectionId"], nil];
    CDVInvokedUrlCommand* newCommand = [[CDVInvokedUrlCommand alloc] initWithArguments:args callbackId:@"" className:self.VoIPPushClassName methodName:self.VoIPPushMethodName];
    
    // Store URL and Call Id so they can be used for call Answer/Reject 
    callBackUrl = [caller valueForKey:@"CallbackUrl"];
    callId = [caller valueForKey:@"ConnectionId"];
    if ([[caller valueForKey:@"CancelPush"] isEqualToString:@"true"]) {
        isCancelPush = YES;
    } else {
        isCancelPush = NO;
    }
    if (!isCancelPush) {
        // Notify Webhook that VOIP Push Has been received and app is started
        NSURL *statusUpdateUrl = [NSURL URLWithString:[NSString stringWithFormat:@"%@?id=%@&input=%@", callBackUrl, callId, @"connected"]];
        NSURLSession *session = [NSURLSession sharedSession];
        [[session dataTaskWithURL:statusUpdateUrl
                  completionHandler:^(NSData *statusUpdateData,
                                      NSURLResponse *statusUpdateResponse,
                                      NSError *statusUpdateError) {
                    // handle response
        }] resume];
    }

    [self receiveCall:newCommand];
    @try {
        NSError * err;
        NSData * jsonData = [NSJSONSerialization dataWithJSONObject:data options:0 error:&err];
        NSString * dataString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        [results setObject:dataString forKey:@"extra"];
        
        
    }
    @catch (NSException *exception) {
        NSLog(@"[objC] error: %@", exception.reason);
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:exception.reason];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.VoIPPushCallbackId];
        return;
    }
    @finally {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:results];
        [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.VoIPPushCallbackId];
        completion();
    }
}
@end
