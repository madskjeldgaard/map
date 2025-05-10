MIDIMap {
    var <midiActions; // Dictionary to store MIDI mappings
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
    var window, layout, scrollView, parameterSection;
    var keyboardView, keyboardButtons;
    var pageButtons, pageLabel;
    var hasNoteMappings = false;
    classvar noteNames;

    *new { |midiMap|
        ^super.newCopyArgs(midiMap).init;
    }

    *initClass {
        noteNames = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
    }

    init {
        window = Window("MIDI Map GUI", Rect(100, 100, 800, 600)).front;
        window.onClose = { this.free };

        layout = VLayout();
        window.layout = layout;

        this.createPageControls;
        this.checkNoteMappings; // Check if we need keyboard
        this.makeParameterSection;
        this.updatePageDisplay;
    }

    checkNoteMappings {
        // Check if there are any note mappings
        hasNoteMappings = midiMap.midiActions.keys.any { |key|
            [\noteOn, \noteOff].includes(key[0])
        };
    }

    createPageControls {
        var pageLayout = HLayout(
            [StaticText().string_("Page:").fixedWidth_(40), align: \center],
            [pageLabel = StaticText().string_("0").fixedWidth_(30), align: \center],
            [Button()
                .states_([["<"]])
                .fixedWidth_(30)
                .action_({
                    midiMap.setPage(midiMap.getPage - 1);
                    this.updatePageDisplay;
                    this.checkNoteMappings;
                    this.makeParameterSection;
                }), align: \center],
            [Button()
                .states_([[">"]])
                .fixedWidth_(30)
                .action_({
                    midiMap.setPage(midiMap.getPage + 1);
                    this.updatePageDisplay;
                    this.checkNoteMappings;
                    this.makeParameterSection;
                }), align: \center],
            nil // Right spacer
        );

        layout.add(pageLayout, [margin: [0, 0, 5, 0]]);
    }

    updatePageDisplay {
        { pageLabel.string = midiMap.getPage.asString; }.defer;
    }

    createKeyboard {
        keyboardView = View();
        keyboardButtons = Array.newClear(12);

        keyboardView.layout = HLayout([nil, stretch: 1]); // Left spacer

        // Create 12 keys (MIDI notes 60-71)
        12.do { |i|
            var note = 60 + i;
            var whiteKey = [0, 2, 4, 5, 7, 9, 11].includes(i); // C,D,E,F,G,A,B
            var width = if(whiteKey) { 40 } { 30 };
            var height = if(whiteKey) { 120 } { 80 };
            var color = if(whiteKey) { Color.white } { Color.black };
            var textColor = if(whiteKey) { Color.black } { Color.white };

            keyboardButtons[i] = Button()
                .states_([
                    [noteNames[i], textColor, color],
                    // [noteNames[i], textColor, Color.red]
                ])
                .fixedSize_(Size(width, height))
                .mouseDownAction_({ |btn|
                    var midiKey = [\noteOn, 0, note];
                    if(midiMap.midiActions[midiKey].notNil) {
                        midiMap.midiActions[midiKey].value(127, midiMap);
                    };
                })
                .mouseUpAction_({ |btn|
                    var midiKey = [\noteOff, 0, note];
                    if(midiMap.midiActions[midiKey].notNil) {
                        midiMap.midiActions[midiKey].value(0, midiMap);
                    };
                });

            keyboardView.layout.add(keyboardButtons[i], [spacing: 1, align: \top]);
        };

        keyboardView.layout.add([nil, stretch: 1]); // Right spacer
        layout.insert(keyboardView, 1, [align: \top, margin: [0, 0, 5, 0]]); // Insert after page controls
    }

    removeKeyboard {
        if(keyboardView.notNil) {
            layout.remove(keyboardView);
            keyboardView = nil;
        };
    }

    makeParameterSection {
        var numParams = if(parameterSection.notNil) { parameterSection.children.size } { 0 };

        // Remove existing parameter section if it exists
        if (parameterSection.notNil) {
            parameterSection.remove;
            parameterSection = nil;
        };

        // Create new parameter section
        parameterSection = this.makeParameterViews;

        // Always use ScrollView to ensure consistent behavior
        scrollView = scrollView ?? {ScrollView()
        .hasBorder_(true)
        .autohidesScrollers_(false)};

        scrollView
        .canvas_(parameterSection);

        // Add at correct position in layout
        layout.insert(scrollView, hasNoteMappings.if(2, 1));

        // Resize window if needed
        if(numParams != midiMap.midiActions.size) {
            { window.view.resizeToHint }.defer(0.07);
        };
    }

    makeParameterViews {
        var view = View().layout_(VLayout([nil, stretch: 1])); // Top spacer
        var sortedKeys = midiMap.midiActions.keys.asArray.select({ |key|
            if (midiMap.respondsTo(\getPageForMapping)) {
                midiMap.getPageForMapping(key) == midiMap.getPage
            } { true };
        }).sort({ |a, b|
            var typeOrder = [ \cc, \programChange, \pitchBend, \aftertouch, \noteOn, \noteOff];
            var typeA = typeOrder.indexOf(a[0]);
            var typeB = typeOrder.indexOf(b[0]);
            if (typeA == typeB) { a[2] < b[2] } { typeA < typeB };
        });

        // Create or remove keyboard based on note mappings
        if(hasNoteMappings && keyboardView.isNil) {
            this.createKeyboard;
        } {
            if(hasNoteMappings.not && keyboardView.notNil) {
                this.removeKeyboard;
            };
        };

        sortedKeys.do{ |key|
            var rowLayout = HLayout();

            // Label
            rowLayout.add(
                StaticText()
                    .string_(this.formatKey(key))
                    .fixedWidth_(150)
                    .align_(\right),
                [stretch: 1]
            );

            // Control
            switch(key[0],
                \cc, {
                    rowLayout.add(
                        Slider()
                            .orientation_(\horizontal)
                            .value_(0.5)
                            .action_({ |slider|
                                midiMap.midiActions[key].value(
                                    slider.value.linlin(0.0, 1.0, 0, 127).asInteger,
                                    midiMap
                                );
                            }),
                        [stretch: 4]
                    );
                },
                \noteOn, \noteOff, nil, // Skip notes (handled by keyboard)
                { // Other types
                    rowLayout.add(
                        Button()
                            .states_([["Trigger"]])
                            .action_({
                                midiMap.midiActions[key].value(127, midiMap);
                            })
                    );
                }
            );

            // Delete button
            rowLayout.add(
                Button()
                    .states_([["x", Color.white, Color.red]])
                    .fixedWidth_(20)
                    .action_({
                        midiMap.unmap(key[0], key[1], key[2]);
                        this.checkNoteMappings;
                        this.makeParameterSection;
                    })
                    .maxHeight_(20),
                [spacing: 2]
            );

            view.layout.add(rowLayout, [margin: [2, 2, 2, 2]]);
        };

        view.layout.add([nil, stretch: 1]); // Bottom spacer

        // Add Learn button at bottom
        view.layout.add(
            Button()
                .states_([["Learn New MIDI Control"]])
                .action_({ this.startLearnMode }),
            [margin: [5, 5, 5, 5]]
        );

        ^view;
    }

    formatKey { |key|
        var type = key[0], chan = key[1], num = key[2];
        ^switch(type,
            \noteOn, "Note On Ch% %".format(chan, num),
            \noteOff, "Note Off Ch% %".format(chan, num),
            \cc, "CC Ch% %".format(chan, num),
            \programChange, "Prog Ch% %".format(chan, num),
            \pitchBend, "PitchB Ch%".format(chan),
            \aftertouch, "AfterT Ch%".format(chan),
            "Unknown"
        );
    }

    startLearnMode {
        var learnWindow = Window("MIDI Learn", Rect(300, 300, 300, 100)).front;
        var text = StaticText(learnWindow, Rect(10, 10, 280, 30))
            .string_("Move a MIDI control now...")
            .align_(\center);
        var cancelBtn = Button(learnWindow, Rect(10, 50, 280, 40))
            .states_([["Cancel"]])
            .action_({
                midiMap.midiLearnEnabled = false;
                learnWindow.close;
            });

        midiMap.enableMIDILearn({ |val, num, chan, src|
            {
                learnWindow.close;
                this.checkNoteMappings;
                this.makeParameterSection;
            }.defer;
        });
    }

    free {
        // Clean up if needed
    }
}
