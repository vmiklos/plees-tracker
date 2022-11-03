# Version descriptions

## 7.4.3

- Resolves: gh#275 sleep item layout: add dedicated icon for the 'awake for' row
- Fixed the tracking notification to work with Android 13

## 7.4.2

- Resolves: gh#309 dashboard: hide seconds & timezone by default on the dashboard

## 7.4.1

- Resolves: gh#282 lower baseline to API 23 (Android 6.0), covering about 97.4% of devices

## 7.4.0

- Resolves: gh#274 add a start time offset
- Resolves: gh#298 release builds are now signed even outside F-Droid

## 7.3.5

- delete all sleep now confirms before erasing starts

## 7.3.4

- Add menu item to delete all sleeps (Ely M)
- Resolves: gh#285 main service: fix crash on Android 12

## 7.3.3

- Resolves: gh#277 sleep activity: allow multi-line notes

## 7.3.2

- Show timezone at sleep start/stop timestamps
- Runtime baseline is now API 24 (Android 7.0), still covering more than 80% of devices

## 7.3.1

- Resolves: gh#253 fixed sleep activity to allow sleep edits again (Mael Lacour)

## 7.3.0

- Resolves: gh#239 it is now possible to comment sleeps
- No longer using `android:onClick`, which is broken on older versions of Android

## 7.2.5

- Resolves: gh#208 add separators between sleep items in the main activity
- Resolves: gh#209 improved duplicate filtering in the calendar import/export (Jesper Lillqvist)

## 7.2.4

- Resolves: gh#207 avoid duplicate entries when importing from calendar multiple times

## 7.2.3

- Resolves: gh#150 avoid duplicate entries when exporting to calendar multiple times

## 7.2.2

- Resolves: gh#178 prevent negative sleep durations

## 7.2.1

- Resolves: gh#184 fixed overlapped and blocked texts on main page (yuhuitech)

## 7.2.0

- Resolves: gh#161 main activity: make the rating widget read-only

## 7.1.5

- Resolves: gh#157 hint that swiping the sleep will delete it
- Resolves: gh#162 avoid empty screen when scrolling down in the stats activity (usashiki)

## 7.1.4

- Resolves: gh#138 sleep edit / delete: handle automatic backup

## 7.1.3

- Resolves: gh#94 Add quick settings tile to start/stop tracking

## 7.1.2

- Awake-for and rating property of the sleep card is now hidden by default

## 7.1.1

- Resolves: gh#51 add a graphs menu item and activity to chart various stats over time (usashiki)
- Resolves: gh#92 add a widget to start/stop tracking with a single stap from the home screen
- Display time awake after a sleep on the main screen (Sebastian Zeller)
- Resolves: gh#88 Add dashboard duration setting (usashiki)

## 7.1.0

- Resolves gh#90 disable auto-backup bool setting when the user refuses to pick an auto-backup
  folder

## 7.0.5

- Avoid vibration when the sleep notification is created
- Resolves: gh#32 ability to automatically backup to a storage folder, to be used with e.g.
  Nextcloud

## 7.0.4

- Much faster mass-import of sleeps from a previous export result

## 7.0.3

- Resolves: gh#41 ability to export events to your calendar (Ed George)

## 7.0.2

- Added PT-BR translation (fabianski7)
- Tested on Android 11
- Removed not needed custom fonts, now using default regular/bold fonts from the system
- Resolves: gh#33 main activity: don't delete entry by swiping on the rating bar
- Resolves: gh#29 ability to import events from your calendar (Ed George)

## 7.0.1

- Fix missing localization of the notification channel's name
- Updated appcompat, constraintlayout, material, junit and espress-core to latest versions

## 7.0.0

- Resolves: gh#28 it is now possible to rate sleeps
- Resolves: gh#7 expand/collapse FAB on scroll

## 6.4

- Resolves: gh#27 improve main activity FAB color in dark mode
- Added Spannish translation (Diego Sanguinetti)
- Resolves: gh#6 next to all-time stats, there are now "last 7 days" and "this year" stats as well
- Related: gh#1 Cannot import csv after export, improved fix for less mainstream Android flavors
  (Sebastian Zeller)

## 6.3

- Resolves: gh#21 daily average now detects completely skipped days
- Resolves: gh#20 sleep entries are now being sorted in chronological order (Sebastian Zeller)
- Resolves: gh#19 in the sleep edit time picker, use 24 or 12 hour view according to system settings
  (Sebastian Zeller)
- Resolves: gh#16 support dark mode (martiandolphin)

## 6.2

- Resolves: gh#15 export format is now better documented
- Resolves: gh#14 main view: sleep counter is now less confusing for multiple sleeps / day
- Resolves: gh#13 main view: scroll the content above the recycler view
- Resolves: gh#8 main view: the snackbar and the start/stop button doesn't overlap anymore

## 6.1

- Resolves: gh#11 use different colors for start and stop
- Much improved design (Sanju S)
- Resolves: gh#5 Main view: average of daily sum of sleeps is now visible

## 6.0

- Main view: the sleep list now has a scrollbar
- Sleep view: now shows the ID and has a back button

## 5.0

- Resolves: gh#2 Allow the user to manually edit an entry

## 4.0

- App metadata now features a screenshot
- Added an about dialog to credit used libraries
- Now never performing database operations on the main thread
- Resolves: gh#1 Cannot import csv after export

## 3.0

- Can remember already started (but not yet stopped) sleeps on system restart
- Can show duration of each past sleep
- Can delete past recorded sleeps selectively

## 2.0

- Can import previously exported data
- Notification icon is now in sync with the launcher icon
- Runtime baseline is now only API 22 (Android 5.1), not API 26 to cover about 80% of devices

## 1.0

- Initial release
- Can store past sleeps
- Can count average duration of them
