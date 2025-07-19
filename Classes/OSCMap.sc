OSCMap {
    var <oscActions; // Dictionary to store OSC mappings
    var <>oscLearnEnabled = false; // OSC Learn mode flag
    var <oscLearnCallback; // Callback for OSC Learn
    var <oscMappingsPath; // Path to save/load OSC mappings
    var <oscLearnCondition; // Condition for blocking OSC Learn
    var <lastLearnedKey = nil; // Last learned OSC key
    var oscFuncs; // Stores individual OSCFuncs
    var pageChangeFunc;
    var <page=0;
    var <allowedHosts; // Array of allowed host IPs/names
    var <currentHostCheck = true; // Whether to check hosts
    var <dataLayer; // Data layer for storing current values
    var <catchEnabledPaths; // Set of OSC paths that have catch enabled
    var <defaultPort;
    var <>verbose = true;

    *new { |oscMappingsPath, allowedHosts, port=57120|
        ^super.new.init(oscMappingsPath, allowedHosts, port);
    }

    *localIP {
        ^Platform.case(
            \osx,       {
                "ifconfig -l | xargs -n1 ipconfig getifaddr".unixCmdGetStdOut().strip(Char.ret).strip(Char.nl)
            },
            \linux,     {
                "localip not implemented on linux yet".error
            },
            \windows,   {
                "localip not implemented on windows yet".error
            }
        )
    }

    init { |path, hosts, port|
        oscActions = Dictionary.new;
        oscFuncs = Dictionary.new;
        oscLearnCondition = Condition.new;
        oscMappingsPath = path ? "oscMappings.scd";
        allowedHosts = hosts;
        dataLayer = OSCDataLayer.new;
        catchEnabledPaths = Set.new;
        defaultPort = port;

        // Open port if not already open
        if(thisProcess.openPorts.asArray.includes(defaultPort).not) {
            thisProcess.openUDPPort(defaultPort);
            ("Opened OSC port " ++ defaultPort).postln;
        };

        "Send OSC messages to: ip '%', port %".format(
            this.class.localIP, defaultPort
        ).postln;
    }

    // ========== Host Management ==========
    isHostAllowed { |host|
        ^(allowedHosts.isNil or: { currentHostCheck.not } or: {
            allowedHosts.any{|allowed| allowed == host }
        });
    }

    // ========== OSC Mapping ==========
    map { |path, argCount, action, enableCatch=true|
        var key = [path, argCount];

        // Remove existing mapping if present
        this.unmap(path, argCount);

        // Store the action
        oscActions[key] = action;

        // Create a dedicated OSCFunc for this path
        oscFuncs[key] = OSCFunc({ |msg, time, addr|
            var args = msg[1..];
            var host = addr.ip;

            if(verbose) {
                "Received mapped OSC:".postln;
                ("Path: " + path + " (expected args: " + argCount + ")").postln;
                ("Actual args: " + args.size + " values: " + args).postln;
            };

            if(this.isHostAllowed(host) and: {args.size == argCount}) {
                var storedVal = dataLayer.getValue(path, argCount);
                var hasCaught = if(this.isCatchEnabled(path)) {
                    args == storedVal
                } { false };

                dataLayer.setValue(path, argCount, args);

                if(oscLearnEnabled && oscLearnCallback.notNil) {
                    this.handleLearnMode(key, args);
                } {
                    this.executeAction(key, args, hasCaught);
                };
            };
        }, path: path.asSymbol, recvPort: defaultPort).permanent_(true);

        if(enableCatch) { this.enableCatch(path) };

        if(verbose) { ("Created OSC mapping for " ++ path ++ " with " ++ argCount ++ " args").postln };
    }

    unmap { |path, argCount|
        var key = [path, argCount];
        if(oscFuncs[key].notNil) {
            oscFuncs[key].free;
            oscFuncs.removeAt(key);
        };
        oscActions.removeAt(key);
        if(verbose) { ("Removed OSC mapping for " ++ path).postln };
    }

    // ========== OSC Learn ==========
    enableOSCLearn { |callback, actionWhenDone|
        forkIfNeeded{
            oscLearnEnabled = true;
            oscLearnCallback = callback;
            oscLearnCondition.test = false;

            oscLearnCondition.wait;

            if(actionWhenDone.notNil) {
                actionWhenDone.value(lastLearnedKey);
            }
        }
    }

    handleLearnMode { |key, args|
        if(key != lastLearnedKey) {
            oscActions[key] = oscLearnCallback;
            oscLearnEnabled = false;
            lastLearnedKey = key;
            oscLearnCondition.test = true;
            oscLearnCondition.signal;
            ("Learned OSC mapping for " ++ key).postln;
        } {
            if(verbose) { "Ignoring repeat message during learn".postln };
        };
    }

    executeAction { |key, args, hasCaught|
        var action = oscActions[key];
        if(action.notNil) {
            if(this.isCatchEnabled(key[0])) {
                action.value(args, this, hasCaught);
            } {
                action.value(args, this);
            };
        } {
            if(verbose) { ("No action for " ++ key).postln };
        };
    }

    // ========== Data Layer ==========
    enableCatch { |path| catchEnabledPaths.add(path) }
    disableCatch { |path| catchEnabledPaths.remove(path) }
    isCatchEnabled { |path| ^catchEnabledPaths.includes(path) }

    getValue { |path, argCount| ^dataLayer.getValue(path, argCount) }
    setValue { |path, argCount, value| dataLayer.setValue(path, argCount, value) }

    // ========== Persistence ==========
    saveMappings {
        var saveData = oscActions.collectAs({ |action, key|
            [key, action.asCompileString]
        }, Array);

        File.use(oscMappingsPath, "w", { |f|
            f.write(saveData.asCompileString);
        });

        ("Saved OSC mappings to " ++ oscMappingsPath).postln;
    }

    loadMappings {
        var loaded = File.readAllString(oscMappingsPath).interpret;

        loaded.do { |item|
            var key = item[0];
            var action = item[1].interpret;
            this.map(key[0], key[1], action);
        };

        ("Loaded OSC mappings from " ++ oscMappingsPath).postln;
    }

    // ========== Cleanup ==========
    free {
        oscFuncs.do(_.free);
        oscActions.clear;
        oscFuncs.clear;
    }
}

// Data layer for storing OSC values
OSCDataLayer {
    var <values;

    *new {
        ^super.new.init;
    }

    init {
        values = Dictionary.new;
    }

    setValue { |path, argCount, value|
        var key = [path, argCount];
        values[key] = value;
    }

    getValue { |path, argCount|
        var key = [path, argCount];
        ^values[key];
    }
}

+ OSCMap {

}
