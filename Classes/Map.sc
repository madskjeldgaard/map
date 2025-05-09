MIDIMap {
    var <midiActions; // Dictionary to store MIDI mappings
    var <midiLearnEnabled = false; // MIDI Learn mode flag
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

    *new { |midiMappingsPath, allowedDevices|
        ^super.new.init(midiMappingsPath, allowedDevices);
    }

    init { |path, devs|
        midiActions = Dictionary.new;
        midiLearnCondition = Condition.new;
        midiMappingsPath = path ? "midiMappings.scd";
        allowedDevices = devs; // Can be nil (allow all) or array of device names

        this.setupMIDIHandlers;
    }

    // Enable/disable device checking
    checkDevices { |bool = true|
        currentDeviceCheck = bool;
    }

    // Check if a device is allowed
    isDeviceAllowed { |src|

        ^(allowedDevices.isNil or: { currentDeviceCheck.not } or: {
            // var deviceName = MIDIClient.sources.at(src).device;
            var device = switch(src.class,
                MIDIEndPoint, {
                    MIDIClient.sources.detect{|dev| dev == src}
                },
                String,  {
                    MIDIClient.sources.detect{|dev| dev.name == src}
                },
                Integer, {
                    MIDIClient.sources.detect{|dev| dev.uid == src}
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
            if(this.isDeviceAllowed(src)) {
                var key = [\noteOn, chan, num];
                this.handleMIDIEvent(key, val);
            };
        });

        midiHandlers[\noteOff] = MIDIFunc.noteOff({ |val, num, chan, src|
            if(this.isDeviceAllowed(src)) {
                var key = [\noteOff, chan, num];
                this.handleMIDIEvent(key, val);
            };
        });

        midiHandlers[\cc] = MIDIFunc.cc({ |val, num, chan, src|
            if(this.isDeviceAllowed(src)) {
                var key = [\cc, chan, num];
                this.handleMIDIEvent(key, val);
            };
        });

        midiHandlers[\programChange] = MIDIFunc.program({ |val, chan, src|
            if(this.isDeviceAllowed(src)) {
                var key = [\programChange, chan, val];
                this.handleMIDIEvent(key, 1);
            };
        });

        midiHandlers[\pitchBend] = MIDIFunc.bend({ |val, chan, src|
            if(this.isDeviceAllowed(src)) {
                var key = [\pitchBend, chan, val];
                this.handleMIDIEvent(key, val);
            };
        });

        midiHandlers[\aftertouch] = MIDIFunc.touch({ |val, chan, src|
            if(this.isDeviceAllowed(src)) {
                var key = [\aftertouch, chan, val];
                this.handleMIDIEvent(key, val);
            };
        });
    }

    // Handle MIDI events
    handleMIDIEvent { |key, val|
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
                midiActions[key].value(val, this);
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

    // Map a function to a MIDI control
    map { |type, channel, number, action|
        var key = [type, channel, number];

        // If a mapping already exists, overwrite it
        if (midiActions[key].notNil) {
            ("Overwriting existing MIDI mapping for " ++ key).postln;
            this.unmap(type, channel, number);
        };

        midiActions[key] = action;
        ("MIDI control mapped: " ++ key).postln;
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

    gui{
        MIDIMapGUI.new(this);
    }
}

MIDIMapGUI {
    var <midiMap;
    var window, layout, parameterSection;

    *new { |midiMap|
        ^super.newCopyArgs(midiMap).init;
    }

    init {
        window = Window("MIDI Map GUI", Rect(100, 100, 400, 600));
        layout = VLayout.new;
        window.layout = layout;

        this.createControls;
        window.front;
    }

    createControls {
        var maxValue = 127;

        // First sort the keys by type and number
        var sortedKeys = midiMap.midiActions.keys.asArray.sort({ |a, b|
            var typeOrder = [\noteOn, \noteOff, \cc, \programChange, \pitchBend, \aftertouch];
            var typeA = typeOrder.indexOf(a[0]);
            var typeB = typeOrder.indexOf(b[0]);

            if (typeA == typeB) {
                a[2]  < b[2];
            } {
                typeA < typeB;
            };
        });

        parameterSection = View.new.layout_(VLayout.new);

        // Iterate over the sorted keys and create controls for each
        sortedKeys.do{ |key|
            var control, label, layout;
            layout = HLayout.new;

            label = StaticText.new.string_(key.asString);
            layout.add(label);

            if (key[0] == \cc) {
                control = Slider.new
                    .orientation_(\horizontal)
                    .action_({ |slider|
                        midiMap.midiActions[key].value(slider.value.linlin(0.0,1.0,0,127).asInteger, midiMap);
                    });
                layout.add(control);
            } {
                control = Button.new
                    .states_([[key.asString]])
                    .action_({
                        var val = if(key[0] == \noteOn) { maxValue } { 0 };
                        midiMap.midiActions[key].value(val.asInteger, midiMap)
                    });
                layout.add(control);
            };

            parameterSection.layout.add(layout);
        };

        layout.add(parameterSection);
    }
}
