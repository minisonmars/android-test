# Caravan Level

A simple Android bubble level for levelling a caravan, using the phone's built-in accelerometer. No internet connection or special permissions needed.

## Using it

1. Place the phone flat, screen up, anywhere inside the caravan.
2. Point the top edge of the phone toward the front (hitch end), following the **FRONT** arrow on screen.
3. The bubble moves toward the high side, like a real spirit level. It turns green and shows **LEVEL** when within 0.5°.
4. The readouts show side-to-side and front-to-back tilt in degrees, and which side is high.
5. Once caravan sizes are set, it tells you what to do, for example **Raise LEFT side 4 cm** (put the low side on ramps or blocks) and **Lower FRONT 3 cm** or **Raise FRONT 3 cm** (the hitch end, via the jockey wheel). Left and right are as seen standing behind the caravan, looking toward the hitch.

The phone's position on the caravan doesn't matter, because a rigid body tilts by the same angle everywhere. Only the phone's direction matters.

### Buttons

- **Calibrate** zeroes the reading against a surface you know is level (saved per profile).
- **Voice: ON/OFF** switches spoken instructions on. It is off each time the app starts. When on, the phone speaks an instruction once the reading has settled for about two seconds, and says "Level" when you're done.
- **Settings** opens caravan profiles.

### Settings

- Create several caravan profiles, each with its own sizes and calibration.
- **Track width**: wheel centre to wheel centre.
- **Hitch to axle**: coupling to axle centre (use the midpoint between the axles on a twin axle).
- Switch between cm and inches. Sizes are stored in cm internally.
- Tap **Save** to keep changes. The profile selected when you save becomes the active one.

## Getting the APK

The app is built automatically by GitHub Actions on every push to `main`.

Every successful build publishes a **Release** named with its version number (for example `v0.3.12`) with the APK attached.

1. Open the repo's **Releases** page (github.com/minisonmars/android-test/releases).
2. Tap the APK under **Assets** on the latest release and open it on the phone. Allow installs from your browser or files app when prompted.

The version number is the one set in `.github/workflows/build.yml` plus the build number, and the app's version code goes up with every build, so new APKs install over the old one.

The same APK is also attached to each Actions run as a zipped artifact. You can start a build manually from the Actions tab with **Run workflow**.

## Project layout

- `app/src/main/java/com/caravanlevel/MainActivity.kt` – sensors, smoothing, calibration, raise/lower maths and voice
- `app/src/main/java/com/caravanlevel/BubbleView.kt` – bubble level, front arrow and instructions drawing
- `app/src/main/java/com/caravanlevel/SettingsActivity.kt` – profiles and units screen
- `app/src/main/java/com/caravanlevel/Store.kt` – saved profiles
- `.github/workflows/build.yml` – cloud build that produces the APK

## Ideas for later

- Landscape or other phone orientations
- Signed release build for the Play Store
