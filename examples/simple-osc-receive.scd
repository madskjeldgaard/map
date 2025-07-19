(
~localPort = 7777;
~myIP = "ifconfig -l | xargs -n1 ipconfig getifaddr".unixCmdGetStdOut();
"Local IP Address: %".format(~myIP).postln;
"Send OSC messages to this host: %, %".format(~myIP, ~localPort).postln;

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

// And 4 toggles
(1..4).do{|i|
    var path = "/2/toggle%".format(i.asString).asSymbol;
    var numValues = 1;
    m.map(path, numValues, { |val| "RECEIVED %: %".format(path, val).postln });
};

)

// Send test messages
n = NetAddr("127.0.0.1", 7777); // Localhost
n.sendMsg('/ping', 0.75);
n.sendMsg('/2/push1', 1);

OSCFunc.trace(true, true)
OSCFunc.trace(false)
