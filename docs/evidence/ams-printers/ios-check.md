# iOS check on a Mac

Linux cannot run Xcode or an iOS simulator. On a Mac with Xcode and an installed iOS simulator:

1. Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` from the repository root.
2. Open `iosApp/iosApp.xcodeproj` in Xcode, select an iPhone simulator, and run the `iosApp` scheme.
3. Choose Try the demo, open Devices, and tap Configure demo in the banner. Select H2D, four units and AMS 2 Pro; tap Apply. Check both nozzle temperatures and each AMS row. Repeat with zero units, then configure four AMS 2 Pro units and eight additional AMS HT units.
4. Open Filament and check each unit and its slots. No printer connection is needed.

The simulator run is not a substitute for testing a physical printer or the H2D MQTT protocol.
