# Changelog

What changed in each version of Caravan Leveller. The newest is at the top.
Each build uses the "Next" section as its release notes on GitHub, then renames it to the new version.

## Next

- Trip diary: removed "Address from the pin", which wasn't working.

## v0.3.46

- Trip diary: a trip can now have several stops, each with its own arrival and leaving dates, site, pin, pitch, rating, cost, notes, contact details and photos. Tap a stop's name to open or close it, so only the one you're working on is shown in full. Existing trips become one-stop trips.
- Trip diary: once a trip's last leaving date has passed, it opens read-only. Tap "Edit this trip" to change it.
- Trip diary: "Address from the pin" fills in the address from where the pin is, and placing a pin fills it in automatically if the address is empty. "Use where I am now" has gone from trips.
- Sites map: new target button to go to where you are, also when placing a pin.

## v0.3.45

- Sites map: pinching now zooms smoothly instead of in steps, and the map stays filled in while the closer view loads.

## v0.3.44

- Trip diary: "Choose on the map" lets you place a trip's pin by tapping the OpenStreetMap map, and "Move the pin on the map" to change it. "Navigate (maps app)" opens the site in your maps app for directions.

## v0.3.43

- Settings: new Colour choice (green, blue, teal, purple, pink, red, orange, brown or grey) for buttons, icons and vehicle pictures, with a light tint in the background.
- New Trip diary: save each stay with dates, site name, address, map position, pitch number, a star rating, cost, notes, photos and the site's phone, email and website. Call, email or open the website from the trip. Search all trips and sort them by most recent, nearest to you, highest rated or oldest.
- Trip diary map: "Show all sites on a map" shows every site as a pin on an OpenStreetMap map. Drag, pinch or use + and - to zoom, and tap a pin to open the trip or get directions in your maps app.

## v0.3.41

- Compass: the Sunrise and Sunset words now sit clear of the compass ring.
- Level: with no levelling sizes set, the degrees are only shown once, in the bigger orange lines.

## v0.3.40

- Level: Calibrate now asks first, reminding you to do it on a surface you know is level (checked with a proper spirit level).
- Level: the Sizes button and the "add the levelling sizes" note only show until the vehicle's levelling sizes are set.
- Compass: the sun markers say Sunrise and Sunset, and the Sun today section shows how high the sun gets and when.
- Compass: Mobile signal is now above the TV and satellite parts, and can be hidden like them.

## v0.3.39

- Level: the buttons at the bottom are now small tiles with pictures, like the home page.
- Level: before the vehicle's levelling sizes are set, the level (and the voice) tells you which side or end is low and by how many degrees.
- Compass: the mobile signal now updates live, shows 5G when the phone is on 5G, and counts bars the way the status bar does as far as Android allows.

## v0.3.38

- Updates screen: every version now has its own Install button under its notes, so you can pick a version that isn't the latest. Older versions are listed the same way (with "Go back to version X") instead of in a drop-down. Going back still means uninstalling first, because Android won't install an older version over a newer one, and that deletes the app's data unless you save it first

## v0.3.36

- Home page: Compass is now the second tile, next to Level.
- Compass: the compass stays on screen and the text underneath scrolls. The TV aerial and satellite dish parts each have a Hide button, which also takes their marker off the compass. Tap Show to bring them back.

## v0.3.35

- Level, Two bubbles: side to side is a horizontal vial at the top, and front to back is an upright vial below it, front at the top.
- Checklists: every vehicle now has a Packing list of things to take, which you can edit like the others. Tap "Choose countries" on it for a trip abroad and the kit and paperwork those countries need is added at the top. That covers hi-vis jackets, warning triangles, the French breathalyser, beam deflectors, the UK sticker, vignettes and so on, each marked required or recommended. It's general information as the rules were understood in 2026, so check gov.uk and the AA or RAC guides before you go.

## v0.3.34

- New Compass page. It shows true north, and where the sun rises and sets today, with the times. It also has the direction and distance of the nearest main TV transmitters, and where to point a Freesat/Sky dish (direction, up angle and LNB skew). There's the phone's mobile signal too, with a button to Ofcom's coverage map. It uses your rough location, which stays on the phone.

## v0.3.32

- Each vehicle now has a picture of its type in the drop-downs: caravan, campervan, motorhome or tow car.
- Settings: new Backup section. "Back up to a file" saves everything (vehicles, checklists, service records with their photos and files, reminders and settings) in one file, and you choose where to keep it: Google Drive, OneDrive, the phone and so on. "Restore from a backup" puts it all back.
- Reminders: new button to show reminders that have a date in a phone calendar, either one you already have or a new "Caravan Leveller" calendar. The calendar entries change when you change a reminder and go when you delete it. The app asks for calendar access only when you turn this on.

## v0.3.31

- Vehicle info: campervans, motorhomes and tow cars have a new size, "Width including wing mirrors".
- Level: in Two bubbles, the side to side and front to back vials are now one above the other, each labelled at its ends.

## v0.3.30

- Vehicle info: the unit drop-downs next to each size now show on one line.

## v0.3.29

- Vehicle info: "Fill in from a known model" now also has Swift 2021 and 2025 caravans and Bailey 2017 (Pursuit, Pegasus and most Unicorns). Figures are the makers' published ones, so check them against your own caravan's plate.

## v0.3.27

- Level: new Two bubbles / One bubble button. Two bubbles shows separate spirit-level vials, a long one for side to side and an upright one for front to back, each turning green when that direction is level. The app remembers your choice

## v0.3.26

- Settings > Theme: Light (for daytime), Dark (for night-time) or Follow the phone (the default), which switches when your phone does. Every page follows it, including the level

## v0.3.25

- Signed with the apps' own key instead of Android's standard debug key, to cut down Play Protect warnings. It still installs over the old version, keeping your data

## v0.3.24

- Vehicle info: add a photo of the vehicle (take one or choose one from your gallery, in Edit). Tap it to see it full size
- Each size can be typed in mm, cm, m, inches, feet or yards, and is shown in the unit you used (for example 6.40 m, 205.00 cm)
- Standard lines now depend on the type: caravans have CRiS number and VIN (chassis number); campervans, motorhomes and cars have Registration and VIN
- Changing a vehicle's type offers to swap to that type's starting checklists
- Campervan and motorhome checklists have more van checks (fuel and AdBlue, cab seats and blinds, bikes, ramps and more) and no towing checks
- Caravans no longer show mileage, and their reminders are by date only
- Fixed drop-downs sometimes ignoring your first choice

## v0.3.23

- Test update: this one should install without the "Update this app?" pop-up
- Settings > Updates also shows which Android version the phone has

## v0.3.22

- Now built for a newer version of Android (API 35). Newer Android versions only let an app update itself without the "Update this app?" screen if it targets a recent version, so this should fix the pop-up from the next update on
- Screens now fit around the status bar, navigation bar and keyboard on newer phones

## v0.3.21

- Test update: this one should install without Android's "Do you want to update this app?" screen
- Clearer message while an update installs

## v0.3.20

- Fixed updates still asking "Do you want to update this app?". Installing this version will still ask once; updates after it shouldn't

## v0.3.19

- Settings > Updates shows which app Android thinks installed Caravan Leveller. Updates can only skip Android's confirm screen when it says this app

## v0.3.18

- Vehicle info: the standard lines (Make, Model, Year, CRiS number, Registration) keep their names and can't be removed; only lines you add yourself can be renamed or removed

## v0.3.17

- Now a proper release build (signed with the same key, so it installs over the old version), which should cut down Play Protect "harmful app" warnings
- Updates can now install without Android's extra "Do you want to install this update?" screen (Android 12 and later). This starts working from the update after this one, because Android only allows it when the app installed the previous version itself

## v0.3.15

- Drop-down lists now show their options in full (the text was cut off)
- Vehicle info: each line (Make, Model, Fridge...) now shows its name above an empty box to type in, instead of a box with the name already in it. Use Rename or Remove under a line, and Add a line asks for the name first

## v0.3.14

- New home page with big buttons for Level, Checklists, Vehicle info, Service records, Reminders and Settings
- Settings > Open the app on: the home page, where you left off, or a page of your choice
- More than one vehicle: caravan, campervan, motorhome or tow car. The vehicle is shown at the top of every page; pick another from the drop-down. Your caravan profiles have become vehicles, with their sizes and calibration kept
- The level works for campervans and motorhomes too: set the wheelbase and it tells you which wheels to raise on ramps
- Checklists for setting up, packing away and before towing or driving, with starting lists for each vehicle type. Change the wording, reorder, add or remove items, or make new lists. "Start again" unticks everything, and a finished list starts fresh by itself 12 hours later
- Vehicle info: make, model, CRiS or registration, sizes, equipment (models and serial numbers), notes and mileage
- Service records with date, who did it, cost, mileage, notes and photos or files (take a photo or pick a file)
- Reminders by date, by mileage or both, with repeats and common ones to start from (habitation service, MOT, tax, insurance and more). You get a notification 2 weeks before and on the day
- Mileage reminders are estimated from your last reading, your yearly mileage and when you use the vehicle (all year, mostly spring and summer, or only spring and summer)
- Settings: distances in miles or km

## v0.3.13

- New App versions screen, the same as in the other apps: new versions are listed first with what changed in each, then the version you have, with older versions behind a "Show older versions" button
- The app now checks for a new version quietly (when you open it, at most every 6 hours, and every time you open Settings). If there is one, a red line at the top of the main screen says which version is ready; tap it to see what's new and install. The Install/Skip pop-up on start-up is gone
- Settings shows your version under Updates, in red when a new one is ready
- Every version now has proper release notes taken from this changelog

## v0.3.12

- Small change to test the in-app updater

## v0.3.11

- When you open the app and a newer version exists, it asks Install or Skip

## v0.3.10

- Settings > Check for updates downloads and installs new versions from inside the app

## v0.3.9

- Renamed to Caravan Leveller, with a new app icon (a caravan with a spirit-level vial)

## v0.3.8

- Updated the instructions in the README

## v0.3.7

- Every build is now published as a numbered release with the APK attached
