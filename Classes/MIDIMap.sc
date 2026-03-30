MIDIMap {
    var <midiActions; // Dictionary to store MIDI mappings
    var <device;
    var <>midiLearnEnabled = false; // MIDI Learn mode flag
    var <midiLearnCallback; // Callback for MIDI Learn
    var <midiMappingsPath; // Path to save/load MIDI mappings
    var <midiLearnCondition; // Condition for blocking MIDI Learn
    var <lastLearnedKey = nil; // Last learned MIDI key
    var <ignoreCCOnOff = false; // Flag to ignore ccOn/ccOff after CC mapping
    var midiHandlers;
    var pageChangeFunc;
    var <page=0;
    var <allowedDevices; // Array of allowed MIDI device names
    var <currentDeviceCheck = true; // Whether to check devices
    var <midiOut; // MIDIOut object for sending MIDI
    var <midiInDevice; // MIDIIn device to match against
    var <dataLayer; // Data layer for storing current values
    var <catchEnabledCCs; // Set of CC numbers that have catch enabled

    *new { | midiInDevice,midiOut, midiMappingsPath, allowedDevices|
        ^super.new.init(midiInDevice, midiOut, midiMappingsPath, allowedDevices);
    }

    init { |inDevice, outDevice, path, devs|
        midiActions = Dictionary.new;
        midiLearnCondition = Condition.new;
        midiMappingsPath = path ? "midiMappings.scd";
        allowedDevices = devs; // Can be nil (allow all) or array of device names
        dataLayer = MIDIDataLayer.new;
        catchEnabledCCs = Set.new;

        // Store MIDI devices
        midiOut = outDevice;
        midiInDevice = inDevice;

        if(devs.notNil) {
            var success = false;
            devs.do { |dev|
                // Connect to the device if it is not already connected
                if (this.findDevice(dev).notNil && success.not) {
                    success = this.connectDevice(dev);
                };
            };
        };

        this.setupMIDIHandlers;
    }

    // ========== Device Management ==========

    // Connect to a specific MIDI device
    connectDevice { |deviceNameOrUID|
        var newdev = this.findDevice(deviceNameOrUID);
        "Connecting to device: ".post;
        deviceNameOrUID.postln;

        if (newdev.notNil) {
            device = newdev;
            ^true;
        } {
            ^false;
        };
    }

    // Find a device by name or UID
    findDevice { |deviceNameOrUID|
        ^switch(deviceNameOrUID.class,
            String, {
                MIDIClient.sources.detect { |dev| dev.name == deviceNameOrUID }
            },
            Integer, {
                MIDIClient.sources.detect { |dev| dev.uid == deviceNameOrUID }
            },
            MIDIEndPoint, {
                deviceNameOrUID
            }
        );
    }

    // Get the currently connected device
    getDevice {
        ^device;
    }

    // Disconnect current device
    disconnectDevice {
        device = nil;
    }

    // Check if a device is allowed
    isDeviceAllowed { |src|
        ^(allowedDevices.isNil or: { currentDeviceCheck.not } or: {
            var device = switch(src.class,
                MIDIEndPoint, {
                    MIDIClient.sources.detect{|dev| dev == src}
                },
                String,  {
                    MIDIClient.sources.detect{|dev| dev.name == src}
                },
                Integer, {
                    MIDIClient.sources.detect{|dev| dev.uid == src}
                }
            );
            var deviceName;
            var status = false;

            if(device.notNil) {
                deviceName = device.name;
            };

            if(allowedDevices.any{|name| name == deviceName}, {
                status = true;
                ("Found device: " ++ deviceName.asString).postln;
            });

            status
        });
    }

    // Setup MIDI handlers with device checking
    setupMIDIHandlers {
        midiHandlers = Dictionary.new;

        midiHandlers[\noteOn] = MIDIFunc.noteOn({ |val, num, chan, src|
            if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\noteOn, chan, num];
                dataLayer.setValue(\noteOn, chan, num, val);
                this.handleMIDIEvent(key, val);
            };
        }, nil, nil, srcID:midiInDevice.uid);

        midiHandlers[\noteOff] = MIDIFunc.noteOff({ |val, num, chan, src|
            if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\noteOff, chan, num];
                dataLayer.setValue(\noteOff, chan, num, val);
                this.handleMIDIEvent(key, val);
            };
        }, nil, nil, srcID:midiInDevice.uid);

        midiHandlers[\cc] = MIDIFunc.cc({ |val, num, chan, src|
            // if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\cc, chan, num];
                var storedVal = dataLayer.getValue(\cc, chan, num);
                var hasCaught = if (this.isCatchEnabled(num)) {
                    val == storedVal
                } { false };

                "RECEIVED: %, val: %".format(key, val).postln;


                dataLayer.setValue(\cc, chan, num, val);
                this.handleMIDIEvent(key, val, hasCaught);
            // };
        }, nil, nil, srcID: midiInDevice.uid);

        midiHandlers[\programChange] = MIDIFunc.program({ |val, chan, src|
            if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\programChange, chan, val];
                dataLayer.setValue(\programChange, chan, val, 1);
                this.handleMIDIEvent(key, 1);
            };
        }, nil, srcID: midiInDevice.uid);

        midiHandlers[\pitchBend] = MIDIFunc.bend({ |val, chan, src|
            if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\pitchBend, chan, val];
                dataLayer.setValue(\pitchBend, chan, val, val);
                this.handleMIDIEvent(key, val);
            };
        }, nil, srcID: midiInDevice.uid);

        midiHandlers[\aftertouch] = MIDIFunc.touch({ |val, chan, src|
            if(this.isDeviceAllowed(src) && this.isMatchingDevice(src)) {
                var key = [\aftertouch, chan, val];
                dataLayer.setValue(\aftertouch, chan, val, val);
                this.handleMIDIEvent(key, val);
            };
        }, nil, srcID: midiInDevice.uid);
    }

    // Check if the source matches our designated MIDI input device
    isMatchingDevice { |src|
        if(midiInDevice.isNil) { ^true };
        ^midiInDevice == src;
    }

    // Handle MIDI events (updated to pass catch status)
    handleMIDIEvent { |key, val, hasCaught=false|
        if (midiLearnEnabled && midiLearnCallback.notNil) {
            // If MIDI Learn is enabled and the key is not the last learned key
            if (key != lastLearnedKey) {
                // Assign the callback to the key
                midiActions[key] = midiLearnCallback;
                midiLearnEnabled = false; // Disable MIDI Learn after mapping
                lastLearnedKey = key; // Store the last learned key
                midiLearnCondition.test = true; // Unblock the condition
                midiLearnCondition.signal; // Signal that the condition is met
                ("MIDI mapping learned for " ++ key).postln;
            } {
                // Ignore subsequent values from the same control
                ("Ignoring subsequent value from " ++ key ++ " during MIDI Learn").postln;
            };
        } {
            // Otherwise, execute the mapped action
            if (midiActions[key].notNil) {
                // Pass the catch status as an additional argument
                if (key[0] == \cc && this.isCatchEnabled(key[2])) {
                    midiActions[key].value(val, this, hasCaught);
                } {
                    midiActions[key].value(val, this);
                };
            } {
                ("No action mapped for " ++ key).postln;
            };
        };
    }

    // Enable MIDI Learn mode with blocking
    enableMIDILearn { |callback, actionWhenDone|
        forkIfNeeded{
            midiLearnEnabled = true;
            midiLearnCallback = callback;
            midiLearnCondition.test = false; // Reset the condition

            // Block execution until a MIDI control of the specified type is learned
            midiLearnCondition.wait;

            if(actionWhenDone.notNil) {
                actionWhenDone.value(lastLearnedKey);
            }
        }
    }

    // ========== Data Layer and Catch Functionality ==========

    // Enable catch for a specific CC number
    enableCatch { |ccNum|
        catchEnabledCCs.add(ccNum);
    }

    // Disable catch for a specific CC number
    disableCatch { |ccNum|
        catchEnabledCCs.remove(ccNum);
    }

    // Check if catch is enabled for a CC number
    isCatchEnabled { |ccNum|
        ^catchEnabledCCs.includes(ccNum);
    }

    // Get current value from data layer
    getValue { |type, channel, number|
        ^dataLayer.getValue(type, channel, number);
    }

    // Set value in data layer with optional sync to hardware
    setValue { |type, channel, number, value, syncToHardware=true|
        dataLayer.setValue(type, channel, number, value);

        if(syncToHardware && midiOut.notNil) {
            this.sendToHardware(type, channel, number, value);
        };
    }

    // Send a value to the hardware MIDI device
    sendToHardware { |type, channel, number, value|
        if(midiOut.notNil) {
            switch(type,
                \noteOn, {
                    midiOut.noteOn(channel, number, value);
                },
                \noteOff, {
                    midiOut.noteOff(channel, number, value);
                },
                \cc, {
                    midiOut.control(channel, number, value);
                },
                \programChange, {
                    midiOut.program(channel, number);
                },
                \pitchBend, {
                    midiOut.bend(channel, value);
                },
                \aftertouch, {
                    midiOut.touch(channel, value);
                }
            );
        };
    }

    // Sync all current values to hardware
    syncAllToHardware {
        if(midiOut.notNil) {
            // Sync all CC values
            dataLayer.ccData.keysValuesDo { |chan, chanDict|
                chanDict.keysValuesDo { |num, value|
                    midiOut.control(chan, num, value);
                };
            };

            // Sync note on/off values
            dataLayer.noteOnData.keysValuesDo { |chan, chanDict|
                chanDict.keysValuesDo { |num, value|
                    if(value > 0) {
                        midiOut.noteOn(chan, num, value);
                    } {
                        midiOut.noteOff(chan, num);
                    };
                };
            };

            ("Synced all values to hardware").postln;
        };
    }

    // Map a function to a MIDI control
    map { |type, channel, number, action, enableCatch=false, syncFunc|
        var key = [type, channel, number];
        var wrappedAction;

        // If a mapping already exists, overwrite it
        if (midiActions[key].notNil) {
            ("Overwriting existing MIDI mapping for " ++ key).postln;
            this.unmap(type, channel, number);
        };

        // Wrap the action with sync function if provided
        if(syncFunc.notNil) {
            wrappedAction = { |val, obj, hasCaught|
                // Call the user's action
                action.value(val, obj, hasCaught);
                // Call the sync function with value, type, channel, number, and midiOut
                syncFunc.value(val, type, channel, number, midiOut);
            };
        } {
            wrappedAction = action;
        };

        midiActions[key] = wrappedAction;
        ("MIDI control mapped: " ++ key).postln;

        if(enableCatch && type == \cc) {
            this.enableCatch(number);
        };
    }

    // Unmap a MIDI control
    unmap { |type, channel, controlNumber|
        var key = [type, channel, controlNumber];
        midiActions.removeAt(key);
        ("MIDI control unmapped: " ++ key).postln;
    }

    // Save MIDI mappings to a file
    saveMappings {
        var file = File(midiMappingsPath, "w");
        file.write(midiActions.asCompileString);
        file.close;
        ("MIDI mappings saved to " ++ midiMappingsPath).postln;
    }

    // Load MIDI mappings from a file
    loadMappings {
        var file = File(midiMappingsPath, "r");
        if (file.isOpen) {
            midiActions = file.readAllString.interpret;
            file.close;
            ("MIDI mappings loaded from " ++ midiMappingsPath).postln;
        } {
            ("Failed to load MIDI mappings from " ++ midiMappingsPath).postln;
        };
    }

    setPageChangeFunc { |func|
        pageChangeFunc = func;
    }

    setPage { |num|
        page = num;

        if(pageChangeFunc.notNil) {
            pageChangeFunc.value(page, this);
        }
    }

    getPage {
        ^page;
    }

    // Get MIDI out device
    getMidiOut {
        ^midiOut;
    }

    // Set MIDI out device
    setMidiOut { |newMidiOut|
        midiOut = newMidiOut;
    }

    // Get MIDI in device
    getMidiInDevice {
        ^midiInDevice;
    }

    // Set MIDI in device
    setMidiInDevice { |newMidiInDevice|
        midiInDevice = newMidiInDevice;
        // Re-setup handlers with new device
        this.setupMIDIHandlers;
    }

    gui{
        MIDIMapGUI.new(this);
    }
}
