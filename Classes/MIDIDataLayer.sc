MIDIDataLayer {
    var <data; // Nested dictionary: type -> channel -> number -> value

    *new {
        ^super.new.init;
    }

    init {
        data = Dictionary.new;
        // Initialize with common MIDI message types
        [\noteOn, \noteOff, \cc, \programChange, \pitchBend, \aftertouch].do { |type|
            data[type] = Dictionary.new;
        };
    }

    // Set a value in the data layer
    setValue { |type, channel, number, value|
        var channelDict;
        var typeDict = data[type];
        if (typeDict.isNil) {
            typeDict = Dictionary.new;
            data[type] = typeDict;
        };

        channelDict = typeDict[channel];
        if (channelDict.isNil) {
            channelDict = Dictionary.new;
            typeDict[channel] = channelDict;
        };

        channelDict[number] = value;
    }

    // Get a value from the data layer
    getValue { |type, channel, number|
        ^data[type].tryPerform(\at, channel).tryPerform(\at, number);
    }

    // Get all values for a specific type and channel
    getValuesForChannel { |type, channel|
        var typeDict = data[type];
        if (typeDict.notNil) {
            var channelDict = typeDict[channel];
            if (channelDict.notNil) {
                ^channelDict.copy;
            };
        };
        ^Dictionary.new;
    }

    // Clear all data
    clear {
        data.keys.do { |type| data[type].clear };
    }

    // Clear data for a specific type
    clearType { |type|
        data[type].clear;
    }

    // Clear data for a specific type and channel
    clearChannel { |type, channel|
        data[type].removeAt(channel);
    }
}
