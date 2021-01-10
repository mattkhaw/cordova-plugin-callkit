var exec = require('cordova/exec');

class CordovaCall {
    constructor() {
        this.handlers = {};
        const success = result => {
            if (!result) {
                return;
            }
            this.emit(result.eventName, result.data);
        }

        const fail = result => {
            console.error(result);
        }

        setTimeout(() => {
            exec(success, fail, 'CordovaCall', 'init');
        }, 10);
    }

    emit(...args) {
        const eventName = args.shift();

        if (!this.handlers.hasOwnProperty(eventName)) {
            return false;
        }

        for (let i = 0, length = this.handlers[eventName].length; i < length; i++) {
            const callback = this.handlers[eventName][i];
            if (typeof callback === 'function') {
                callback.apply(undefined, args);
            } else {
                console.log(`event handler: ${eventName} must be a function`);
            }
        }

        return true;
    }

    on(eventName, callback) {
        if (!this.handlers.hasOwnProperty(eventName)) {
            this.handlers[eventName] = [];
        }
        this.handlers[eventName].push(callback);
    }
}

exports.init = () => new CordovaCall();

exports.setAppName = function(appName, success, error) {
    exec(success, error, "CordovaCall", "setAppName", [appName]);
};

exports.setIcon = function(iconName, success, error) {
    exec(success, error, "CordovaCall", "setIcon", [iconName]);
};

exports.setRingtone = function(ringtoneName, success, error) {
    exec(success, error, "CordovaCall", "setRingtone", [ringtoneName]);
};

exports.setIncludeInRecents = function(value, success, error) {
    if(typeof value == "boolean") {
        exec(success, error, "CordovaCall", "setIncludeInRecents", [value]);
    } else {
        error("Value Must Be True Or False");
    }
};

exports.setDTMFState = function(value, success, error) {
    if(typeof value == "boolean") {
        exec(success, error, "CordovaCall", "setDTMFState", [value]);
    } else {
        error("Value Must Be True Or False");
    }
};

exports.setVideo = function(value, success, error) {
    if(typeof value == "boolean") {
        exec(success, error, "CordovaCall", "setVideo", [value]);
    } else {
        error("Value Must Be True Or False");
    }
};

exports.receiveCall = function(call, success, error) {
    exec(success, error, "CordovaCall", "receiveCall", [call]);
};

exports.sendCall = function(call, success, error) {
    exec(success, error, "CordovaCall", "sendCall", [call]);
};

exports.connectCall = function(success, error) {
    exec(success, error, "CordovaCall", "connectCall", []);
};

exports.endCall = function(success, error) {
    exec(success, error, "CordovaCall", "endCall", []);
};

exports.mute = function(success, error) {
    exec(success, error, "CordovaCall", "mute", []);
};

exports.unmute = function(success, error) {
    exec(success, error, "CordovaCall", "unmute", []);
};

exports.speakerOn = function(success, error) {
    exec(success, error, "CordovaCall", "speakerOn", []);
};

exports.speakerOff = function(success, error) {
    exec(success, error, "CordovaCall", "speakerOff", []);
};

exports.callNumber = function(to, success, error) {
    exec(success, error, "CordovaCall", "callNumber", [to]);
};
