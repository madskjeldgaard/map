[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/X8X6RXV10)

# Map

Simple MIDI mapping and MIDI learning for SuperCollider.

## Features

- Use simple function callbacks
- MIDI learn
- Save/load mappings to a file

## Usage

### Simple mapping

```supercollider
(
MIDIClient.init;
MIDIIn.connectAll;

// Create a new MIDI mapper
m = MIDIMap.new;

// Map a cc control
m.map(type: \cc, channel: 0, number: 1, action: { |val| ("Value1: " ++ val).postln; });

// Map a noteOn
m.map(type: \noteOn, channel: 0, number: 48, action: { |val| ("Value2: " ++ val).postln; });
)
```

### MIDI Learn

It is possible to use midi learn to map a controller value. If done within a fork, it will block executition until the mapping has been performed, allowing to map multiple controls in sequence.

```supercollider
(
fork{
    MIDIClient.init;
    MIDIIn.connectAll;

    // Create a new MIDI mapper
    m = MIDIMap.new;

    // Map a value
    "Turn a knob on your MIDI controller to map it to a value".postln;
    m.enableMIDILearn({ |val| ("Value1: " ++ val).postln; });

    "Another example: Turn another knob on your MIDI controller to map it to another value".postln;
    m.enableMIDILearn({ |val| ("Value2: " ++ val).postln; });

}
)
```

### Saving/loading mappings

This example shows how to use a mappings file to quickly load a setup, or if no setup is present, make one using midi learn.

```supercollider 
(
var mappingFileName = "my-controller.scd";

fork{

    MIDIClient.init;
    MIDIIn.connectAll;

    // Create a new MIDI mapper
    m = MIDIMap.new(mappingFileName);

    if(PathName(mappingFileName).isFile,{
        m.loadMappings;
    }, {

        // Map a value
        "Turn a knob on your MIDI controller to map it to a value".postln;
        m.enableMIDILearn({ |val| ("Value1: " ++ val).postln; });

        "Turn another knob on your MIDI controller to map it to another value".postln;
        m.enableMIDILearn({ |val| ("Value2: " ++ val).postln; });

        "Saving mappings to file %".format(mappingFileName).postln;
        m.saveMappings;

    });
}
)
```

## Installation

Open up SuperCollider and evaluate the following line of code:
`Quarks.install("https://github.com/madskjeldgaard/map")`
