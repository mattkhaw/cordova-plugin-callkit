#import <Cordova/CDV.h>
#import <PushKit/PushKit.h>
#import <CallKit/CallKit.h>

@interface CordovaCall : CDVPlugin <PKPushRegistryDelegate, CXProviderDelegate>

// PushKit
@property (nonatomic, copy) NSString *VoIPPushCallbackId;
@property (nonatomic, copy) NSString *VoIPPushClassName;
@property (nonatomic, copy) NSString *VoIPPushMethodName;
@property (nonatomic, copy) NSString *VoIPPushToken;

- (void)init:(CDVInvokedUrlCommand*)command;

// CallKit
@property (nonatomic, strong) CXProvider *provider;
@property (nonatomic, strong) CXCallController *callController;

- (void)updateProviderConfig;
- (void)setupAudioSession;

- (void)setAppName:(CDVInvokedUrlCommand*)command;
- (void)setIcon:(CDVInvokedUrlCommand*)command;
- (void)setRingtone:(CDVInvokedUrlCommand*)command;
- (void)setIncludeInRecents:(CDVInvokedUrlCommand*)command;
- (void)setDTMFState:(CDVInvokedUrlCommand*)command;
- (void)setVideo:(CDVInvokedUrlCommand*)command;

- (void)receiveCall:(CDVInvokedUrlCommand*)command;
- (void)sendCall:(CDVInvokedUrlCommand*)command;
- (void)connectCall:(CDVInvokedUrlCommand*)command;
- (void)endCall:(CDVInvokedUrlCommand*)command;
- (void)registerEvent:(CDVInvokedUrlCommand*)command;
- (void)mute:(CDVInvokedUrlCommand*)command;
- (void)unmute:(CDVInvokedUrlCommand*)command;
- (void)speakerOn:(CDVInvokedUrlCommand*)command;
- (void)speakerOff:(CDVInvokedUrlCommand*)command;
- (void)callNumber:(CDVInvokedUrlCommand*)command;

- (void)receiveCallFromRecents:(NSNotification *) notification;
- (void)handleAudioRouteChange:(NSNotification *) notification;

@end
