MIDIMap {
    var <midiActions; // Dictionary to store MIDI mappings
    var <midiLearnEnabled = false; // MIDI Learn mode flag
    var <midiLearnCallback; // Callback for MIDI Learn
    var <midiMappingsPath; // Path to save/load MIDI mappings
    var <midiLearnCondition; // Condition for blocking MIDI Learn
    var <lastLearnedKey = nil; // Last learned MIDI key
    var <ignoreCCOnOff = false; // Flag to ignore ccOn/ccOff after CC mapping

    var midiHandlers;

    var <page=0;

    *new { |midiMappingsPath|
        ^super.new.init(midiMappingsPath);
    }

    init { |path|
        midiActions = Dictionary.new;
        midiLearnCondition = Condition.new; // Initialize the condition
        midiMappingsPath = path ? "midiMappings.scd"; // Default path for saving/loading mappings

        // Set up MIDI handlers
        this.setupMIDIHandlers;
    }

    // Setup MIDI handlers for noteOn, noteOff, and CC events
    setupMIDIHandlers {

        midiHandlers = Dictionary.new;

        midiHandlers[\noteOn] = MIDIFunc.noteOn({ |val, num, chan|
            var key = [\noteOn, chan, num];
            this.handleMIDIEvent(key, val);
        });

        midiHandlers[\noteOff] = MIDIFunc.noteOff({ |val, num, chan|
            var key = [\noteOff, chan, num];
            this.handleMIDIEvent(key, val);
        });

        midiHandlers[\cc] = MIDIFunc.cc({ |val, num, chan|
            var key = [\cc, chan, num];
            this.handleMIDIEvent(key, val);
        });

        // Program change handler
        midiHandlers[\programChange] = MIDIFunc.program({ |val, chan|
            var key = [\programChange, chan, val];
            this.handleMIDIEvent(key, 1);
        });

        // Pitch bend handler
        midiHandlers[\pitchBend] = MIDIFunc.bend({ |val, chan|
            var key = [\pitchBend, chan, val];
            this.handleMIDIEvent(key, val);
        });

        // Aftertouch handler
        midiHandlers[\aftertouch] = MIDIFunc.touch({ |val, chan|
            var key = [\aftertouch, chan, val];
            this.handleMIDIEvent(key, val);
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

    setPage { |num|
        page = num;
    }
}
