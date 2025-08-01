[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/X8X6RXV10)

![screen.png](screen.png) 

# Map

Simple controller mapping and MIDI learning for SuperCollider.

Use this to connect any MIDI or OSC controller to Supercollider and control stuff. It has MIDI learn, an automatic gui, mappings you can save/load from disk, etc.

The design goal of this package is to replace all the specific (and differing) packages that exist for specific MIDI controllers with one simple, robust and flexible interface, so you only have to deal with this one interface. And also add functionality from DAWs and other softwares that allow easy mapping and managing of controllers. 

## Features

- Use simple function callbacks
- An automatic GUI for organizing and testing mappings using buttons and sliders.
- MIDI learn
- Save/load mappings to a file
- Organize mappings in pages
- Match against specific MIDI devices. 
- Supports OSC devices through the `OSCMap` class

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

### With GUI

This example constructs a GUI. 

```supercollider
(
var numCcs = 8;
var midiChan = 0;

// Initialize MIDI
MIDIClient.init;
MIDIIn.connectAll;

// Create MIDI map
~midiMap = MIDIMap.new;

~midiMap.setPageChangeFunc({|page, mapper| "Page changed! Now it's on %".format(page).postln });

numCcs.do{|ccNum|
    ~midiMap.map(\cc, midiChan, ccNum, { |val, mapper| "CC %: %. Page: %".format(ccNum, val, mapper.getPage).postln });
};

128.do{|noteNum|
    ~midiMap.map(\noteOn, midiChan, noteNum, { |val, mapper| "Note On %: %. Page: %".format(noteNum, val, mapper.getPage).postln });
    ~midiMap.map(\noteOff, midiChan, noteNum, { |val, mapper| "Note Off %: %. Page: %".format(noteNum, val, mapper.getPage).postln });
};

// Open GUI
~midiMap.gui;
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

### Pages

Sometimes it's useful to organize your mappings in pages or layers. One usecase is for example when your controller does not have many physical controls, you can dedicate some of them to changing the page and thus get a lot more control out of the same controller. 

In this example we map a control to change the page number, and then map another control to change the value of the control on the current page.

Below it's limited to the numbers 0 to 3, but you can set this to as many pages as you like, and you can also use symbols or strings to match instead of numbers.

```supercollider
(
MIDIClient.init;
MIDIIn.connectAll;

// Create a new MIDI mapper
m = MIDIMap.new;

m.setPageChangeFunc({ |newPage, mapper|
    "Page changed to %".format(newPage).postln;
});

// Map a cc control with page numbers
m.map(type: \cc, channel: 0, number: 1, action: { |val, mapper|
    switch (mapper.page,
        0, {
            "Page 0 value: %".format(val).postln;
        },
        1, {
            "Page 1 value: %".format(val).postln;
        },
        2, {
            "Page 2 value: %".format(val).postln;
        },
        3, {
            "Page 3 value: %".format(val).postln;
        }
    );
});

// Map another CC value to change the page number
m.map(type: \cc, channel: 0, number: 2, action: { |val, mapper|
    var newPage = val.linlin(0, 127, 0, 3).asInteger;
    mapper.setPage(newPage);
    "Page number slider changed to %".format(newPage).postln;
});

m.gui;
)

```

### GUI

A simple GUI is available to quickly test out the controls if you are away from your hardware controller.

```supercollider
(
var midiMap = MIDIMap.new;
midiMap.map(\cc, 1, 10, { |val| "CC 10: %".format(val).postln });
midiMap.map(\noteOn, 1, 60, { "Note On 60".postln });
midiMap.map(\noteOff, 1, 60, { "Note Off 60".postln });

midiMap.gui;
)
```

### OSC mapping

```supercollider
(
~localPort = 7777;

// Create an OSCMap allowing messages from localhost
m = OSCMap.new(port: ~localPort);

// Map a specific OSC path with 1 argument
m.map('/ping', 0, { |val| "VAL: %".format(val).postln });

// This uses the TouchOSC mk1 simple layout with 16 push buttons
(1..16).do{|i|
    var path = "/2/push%".format(i.asString).asSymbol;
    var numValues = 1;
    m.map(path, numValues, { |val| "RECEIVED %: %".format(path, val).postln });
};

)

// Send test messages
n = NetAddr("127.0.0.1", ~localPort); // Localhost
n.sendMsg('/ping', 0.75);
n.sendMsg('/2/push1', 1);
```

## Installation

Open up SuperCollider and evaluate the following line of code:
`Quarks.install("https://github.com/madskjeldgaard/map")`
