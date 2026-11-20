# Caravan Level

A simple Android bubble level for levelling a caravan, using the phone's built-in accelerometer. No internet connection or special permissions needed.

## Using it

1. Place the phone flat, screen up, on a level-ish surface inside the caravan.
2. Point the top edge of the phone toward the front (hitch end) of the caravan.
3. The bubble moves toward the high side, like a real spirit level. It turns green and shows **LEVEL** when within 0.5°.
4. The readouts show side-to-side and front-to-back tilt in degrees, and which side is high.

**Calibrate** zeroes the reading against a surface you know is level, which corrects for a phone case or a slightly uneven phone. **Reset** clears the calibration.

## Getting the APK

The app is built automatically by GitHub Actions on every push to `main`.

1. Go to the **Actions** tab and open the latest **Build APK** run.
2. Download the **caravan-level-debug-apk** artifact at the bottom of the run page (use the desktop site in a mobile browser if needed).
3. Unzip it and open `app-debug.apk` on the phone. Allow installs from your browser or files app when prompted.

You can also start a build manually from the Actions tab with **Run workflow**.

## Project layout

- `app/src/main/java/com/caravanlevel/MainActivity.kt` – sensor handling, smoothing and calibration
- `app/src/main/java/com/caravanlevel/BubbleView.kt` – bubble level drawing
- `.github/workflows/build.yml` – cloud build that produces the APK

## Ideas for later

- Show how far to raise each wheel (needs track width and axle-to-hitch distance)
- Landscape or other phone orientations
- Signed release build for the Play Store
