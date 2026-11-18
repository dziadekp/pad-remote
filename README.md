# Pad Remote

A one-page Bluetooth remote for a PitPat-T01 walking pad (DeerRun), so the PitPat app isn't needed.
Open it in Chrome on Android, tap Connect, pick the pad.

- Protocol: service `FFFF`, write `FF01` (with response), notify `FF02`.
- The pad sends status about once a second and must get a reply to each one, or it drops the link after about 10 seconds.
- Speed is in metres per hour (km/h × 1000), from 1000 to 6000. Distance is in metres. The pad shows mph.
- Command format credit: hdxsfbr/walkingpad, azmke/pitpat-treadmill-control.

## Android app (main remote)

`android/` is a small native app with a home-screen widget. It needs no internet. Build and install it to the phone from this Mac:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
android/gradlew -p android testDebugUnitTest assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Android splits the 56-byte status frame into 20-byte pieces unless the app asks for a bigger MTU. The app asks for 247 (the phone settles on 512) and also glues pieces back together.
Verified on the pad 2026-11-18: start, stop, +/− and live speed, distance and time all match the pad's display.
