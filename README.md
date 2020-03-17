# cordova-plugin-callkit
Cordova plugin that enables CallKit + PushKit (iOS) &amp; ConnectionService (Android) functionality to display native UI.

This plugin is basically a merged of 2 plugins, which are, [WebsiteBeaver/CordovaCall](https://github.com/WebsiteBeaver/CordovaCall) and [Hitman666/cordova-ios-voip-push](https://github.com/Hitman666/cordova-ios-voip-push), to basically fulfill iOS 13's requirement for VOIP Push Notification. All credits goes to both of them.

For those who are unaware of iOS 13's requirement for VOIP Push Notification, the change being made here is once your app receives a VOIP Push Notification, it is mandatory to inform the OS that there's a new incoming call, otherwise your app will be forced terminated and banned from getting further VOIP Push Notifications if there's too many failed attempts until the app is reinstalled.

Why the merge is essential? This is essential because in iOS 13, Cordova WebView won't initialize if the app is not started, upon receiving the VOIP Push Notification. It is started only when the incoming call is reported hence, everything must be handled natively.

# Install

Add the plugin to your Cordova project:

`cordova plugin add cordova-plugin-callkit`

# API Guide

For this, just refer to [WebsiteBeaver/CordovaCall](https://github.com/WebsiteBeaver/CordovaCall) and [Hitman666/cordova-ios-voip-push](https://github.com/Hitman666/cordova-ios-voip-push). I'm not gonna be bothered to merge the documentations at all since both of them already provide excellent guides on how to use them. The namespaces in this plugin are identical to both of the repos since this plugin combines both of them into one, like I mentioned above.

# Usage

Once the plugin is installed, the only thing that you need to do is to push a VOIP notification with the following data payload structure:

```javascript
{
  Caller: {
    Username: 'Display Name',
    ConnectionId: 'Unique Call ID'
  }
}
```

If you need more parameters, just add them into the structure to your liking. Basically, the `Username` property is mapped to the name displayed on the call screen and the `ConnectionId` property is mapped to the unique ID for the incoming call (value is optional but property must be provided in the object).

You guys might be wondering, so far it is all about iOS, how about Android? As for Android, no modifications are required for the original plugin since background notifications can handle everything.

# Conclusion

I hope that this plugin will help other people out there who is struggling to figure this portion out. Again, I wanted to thank the original creators of these plugins. Without them, I couldn't figure out on how to do all this. If there's any questions, feel free to contact me. I'll try my best to help.
