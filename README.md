# Caravan Leveller

An Android app for caravans, campervans and motorhomes: a bubble level that tells you how much to raise or lower, plus checklists, vehicle details, service records and reminders. It works offline; the internet is only used to check for app updates.

## Home page

The app opens on a home page with a big button for each part: **Level**, **Checklists**, **Vehicle info**, **Service records**, **Reminders** and **Settings**. If a reminder is due within 30 days it is shown under the vehicle (amber, or red when overdue).

**Settings > Open the app on** lets it open on the home page (default), where you left off, or always on one page. The home page is one tap back from anywhere.

## Vehicles

You can have several vehicles: caravan, campervan, motorhome or tow car. The chosen vehicle is shown at the top of every page; pick another from the drop-down and the whole app switches to it. Add or delete vehicles on **Vehicle info**. (Caravan profiles from older versions became vehicles, keeping their sizes and calibration.)

**Vehicle info** holds:
- Name and type, and details such as make, model, year, CRiS number or registration (add your own lines)
- Sizes: overall length, body length (caravans), width and height, in cm or inches
- Levelling sizes used by the level: track width, plus hitch to axle (caravan) or wheelbase (campervan, motorhome)
- Equipment: fridge, heaters, pump, battery and so on, with makes, models and serial numbers
- Mileage: odometer readings (a caravan has none, so start from 0), estimated miles per year and when you use it
- Notes

Everything is saved as soon as you leave the page.

## Checklists

Each vehicle has its own lists, starting with **Setting up**, **Packing away** and **Before towing** (caravan) or **Before driving** (campervan, motorhome). Tick items as you go. **Start again** unticks everything; a finished list also starts fresh by itself 12 hours later, ready for the next trip. **Edit this list** lets you change the wording, move items up or down, add or remove items, rename or delete the list, or put back the starting list. **New list** makes your own.

## Service records

Add services, repairs and parts fitted with the date, who did it, cost, mileage and notes. Attach photos (take one with the camera) or files such as PDF invoices; tap one to open it. Records show newest first with the total spent.

## Reminders

Set a reminder by date, by mileage, or both (whichever comes first), with optional repeats (every so many months or miles). Start from a common one: habitation service, MOT, road tax, vehicle service, insurance, tyre checks, battery charging and more. **Done** moves a repeating reminder on to its next date.

You get a phone notification 14 days before something is due and again on the day (the app checks once a day, around 9 am, and after the phone restarts). On Android 13 and later it asks to allow notifications the first time you add a reminder.

Mileage reminders work out a rough date from the last reading, the miles per year and how you use the vehicle: all year (same every month), mostly spring and summer (April to September counts three times as much as the other months) or only spring and summer.

## Using the level

1. Pick the vehicle at the top.
2. Place the phone flat, screen up, anywhere inside the caravan, ideally on the floor near the middle.
3. Point the top edge of the phone toward the front (hitch end), following the **FRONT** arrow on screen.
4. The bubble moves toward the high side, like a real spirit level. It turns green and shows **LEVEL** when within 0.5°.
5. The readouts show side-to-side and front-to-back tilt in degrees, and which side is high.
6. Once the levelling sizes are set (tap **Sizes**), it tells you what to do, for example **Raise LEFT side 4.00 cm** (put the low side on ramps or blocks) and **Lower FRONT 3.50 cm** or **Raise FRONT 3.50 cm** (change the hitch height with the jockey wheel). For a campervan or motorhome it says which wheels to raise on ramps, for example **Raise REAR wheels 3.50 cm**. Left and right are as seen standing behind the caravan, looking toward the hitch.

The written figures refresh once a second so they are easy to read, while the bubble itself moves smoothly. Amounts always show two decimals and are rounded to the nearest 0.5 cm or 0.25 in.

The phone's position on the caravan doesn't matter, because a rigid body tilts by the same angle everywhere. Only the phone's direction matters.

### Suggested order

1. Level **side to side** first, before unhitching: put a ramp or block under the low wheel and pull forward onto it (don't reverse up it).
2. Apply the handbrake and chock the wheels.
3. Unhitch, then level **front to back** with the jockey wheel.
4. Wind down the corner steadies last. They steady the caravan, they don't level it.
5. Recheck after a while, as the caravan can settle.

### Buttons

- **Calibrate** zeroes the reading against a surface you know is level (saved per vehicle; clear it on Vehicle info).
- **Voice: ON/OFF** switches spoken instructions on. It is off each time the page opens. When on, the phone repeats the current adjustment every 3 seconds (it waits if the last message is still being spoken). It says "Level" once when you reach level, then stays quiet until you move out of level again.
- **Sizes** opens Vehicle info.

## Settings

- Open the app on: home page, where you left off, or a chosen page
- Sizes in cm or inches, distances in miles or km (stored in cm and miles inside the app)
- Updates (see below)

## Getting the APK

The app is built automatically by GitHub Actions on every push to `main`.

Every successful build publishes a **Release** named with its version number (for example `v0.3.12`) with the APK attached.

1. Open the repo's **Releases** page (github.com/minisonmars/android-test/releases).
2. Tap the APK under **Assets** on the latest release and open it on the phone. Allow installs from your browser or files app when prompted.

The version number is the one set in `.github/workflows/build.yml` plus the build number. The app's version code goes up with every build, and every build is signed with the same key (`app/debug.keystore`), so new APKs install over the old one and keep your saved profiles. That key is for personal testing only and is not suitable for the Play Store.

The same APK is also attached to each Actions run as a zipped artifact. You can start a build manually from the Actions tab with **Run workflow**.

## Updating the app

The app updates itself from this repo's GitHub releases, the same way as all the other apps in this project.

- When you open the app it quietly checks GitHub (at most every 6 hours, and every time you open Settings). If a newer version exists, a red line at the top of the home page says "Version X is ready to install (you have Y)". Tap it to open the versions screen.
- **Settings > Updates** shows your version (red when a new one is ready). **App versions and updates** opens the versions screen:
  1. New versions first, newest at the top, each with its date, size and what changed, and an **Install version X** button.
  2. **Installed: version Y** in green, with its notes.
  3. **Show older versions (n)**: pick one from the list to read its notes. **Go back to this version** explains how (Android won't install an older version over a newer one, so you download it, uninstall, then install it; your saved data is lost).
  4. **Reinstall version Y**.
- The first time, Android asks you to allow Caravan Leveller to install apps ("Allow from this source"); after that you just confirm the update on Android's own screen.

This works because every build is signed with the same key (`app/debug.keystore`) and the repo and its releases are public.

### Release notes

What changed is kept in `CHANGELOG.md`. New user-facing changes go as bullets under `## Next`. Each build uses that section as its GitHub release notes, renames it to the new version, commits the changelog back to `main` and copies every section to its release on GitHub (`.github/scripts/changelog.py`).

## Project layout

All in `app/src/main/java/com/caravanlevel/`:

- `MainActivity.kt` – home page with the section tiles, start-page setting, update banner
- `LevelActivity.kt` and `BubbleView.kt` – sensors, smoothing, calibration, raise/lower maths, voice, and the bubble drawing
- `ChecklistActivity.kt` – checklists and the list editor
- `VehicleActivity.kt` – vehicle details, sizes, equipment, mileage; add and delete vehicles
- `ServiceActivity.kt`, `ServiceEditActivity.kt` – service records with photos and files
- `AttachmentProvider.kt` – lets the camera save into the app and viewer apps open attachments
- `RemindersActivity.kt`, `Reminders.kt` – reminders, the daily check and notifications
- `SettingsActivity.kt` – start page, units, updates
- `Garage.kt` – vehicles and everything in them, saved as one file (`garage.json`); starting checklists
- `Prefs.kt` – app settings, units and dates, mileage estimate, when reminders are due
- `Ui.kt` – shared screen pieces: icons drawn in code, buttons, vehicle picker, page layout
- `Store.kt` – old caravan profiles (read once to make the first vehicles) and number formatting
- `Updater.kt` and `InstallReceiver.kt` – automatic update check, versions screen, download and install

Other files:

- `app/debug.keystore` – fixed signing key so updates install over the old version
- `app/src/main/res` – the app icon (a caravan with a spirit-level vial) and the notification bell, drawn as vectors
- `.github/workflows/build.yml` – cloud build that produces the APK and publishes the release
- `.github/scripts/changelog.py` and `CHANGELOG.md` – release notes

## Ideas for later

- Next stage: weight and payload (MIQM, MTPLM, noseweight, 85% towing guide), tyre pressures and tyre age, document reminders, gas bottle and battery log
- After that: site log with notes, GPS and photos; beginner troubleshooting guides; backup and export to a file
- Landscape or other phone orientations
- Signed release build for the Play Store
