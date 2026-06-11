# Usage

## Main activity

plees-tracker is essentially a stopwatch application. It intentionally requires you to manually
track your sleep with explicitly starting and ending a tracked sleep. This has the benefit that it's
simple: causing no battery drain, nor any privacy problems.

This activity allows:

- Seeing the status of the tracking: not yet started, in progress and finished.

- Dashboard: the number of all tracked sleeps, average of sleep durations (disabled by default) and
  a daily average (in case you sleep multiple times a day or you sometimes skip a whole day) for a
  customizable duration (see the preferences activity below).

- A list of past sleeps for the chosen duration: start/stop time for each sleep, awake time and
  duration counted from these and a rating you can manually specify after the tracking stopped.
  The awake time of the latest sleep depends on the current time, so it'll increase if you restart
  plees-tracker.

- Swiping a sleep left/right will remove the sleep.

- Tapping on a sleep allows getting to a dedicated sleep activity for a single sleep.

- A floating action button at the bottom right corner allows to actually start / stop the tracking.

The menu of this activity allows:

- Import/export your sleeps to CSV. The start and stop columns are UNIX timestamps in milliseconds.
  The import is incremental, i.e. it remembers what items are imported and the next time only newer
  items will be imported.

- Import/export your sleeps to calendar. The export is incremental, i.e. it remembers what items are
  exported, and the next time only newer items will be exported.

- See more stats on your sleeps (see the sleep activity below).

- Customize settings (see the preferences activity below).

## Toggle widget

A widget can be added to your home screen. This allows starting or stopping the tracking with a
single tap: i.e. it's the same as opening the app and tapping on the start/stop button.

## Quick settings tile

A quick settings tile can be added to your panel. This allows starting or stopping the tracking with
a single tap: i.e. it's the same as opening the app and tapping on the start/stop button.

## Preferences activity

### Theme

This allows manually setting the dark mode for plees-tracker. This is useful on Android versions <=
9, where there is no system-provided dark mode. This works out of the box on newer Android versions.

### Backup

Backup settings start with an "Automatic backup" switch, which is off by default. Turning it on for
the first time asks where to back up to; the switch turns itself back off when the last backup
destination is removed. When it's off, no backups run and any configured destinations below it are
greyed out (they are kept, so turning the switch back on resumes backups unchanged).

Backups go to the configured destinations, which are shown as rows in the settings: one device
folder and (in the `gplay` flavor) one Google Drive account can be configured, separately or
together.

"Add backup destination" opens a chooser where both "This device (folder)" and "Google Drive" can be
ticked. Once one type is configured, the row's label adapts ("Add device backup destination" / "Add
Google Drive backup destination") and adding the remaining type asks whether the new destination is
in addition to or instead of the existing one. "Instead of Google Drive" permanently deletes the
Drive backup; switching to Drive instead of the folder asks whether to also delete the folder's
backup.csv. The add row disappears once both types are configured.

Folder backups write a backup.csv file into the chosen folder. This is useful in case you select a
path which is then implicitly synchronized to some external server, e.g. Nextcloud. Tapping the
folder row offers: Change path and Remove folder. Removing the folder offers an optional "Also
delete the backup file (backup.csv)" checkbox when a backup exists; the file is kept unless that is
ticked.

Pretty backup allows you to create a CSV file which has human-readable start, stop and length values
during exporting to a file. This pretty output can't be imported back, though.

#### Google Drive

The Google Drive destination is only available in the `gplay` build flavor (the F-Droid `foss`
flavor is free of proprietary Google dependencies, so it only offers the folder destination).

When you add Google Drive, you sign in with a Google account and your sleeps are backed up to your
Drive's hidden per-app storage (the appDataFolder): it's not visible among your normal Drive files
and is only accessible to this app.

A newly added account backs up once a day by default; "Change frequency" switches between "Once a
day" and "On sleep add / edit / remove". Automatic uploads are skipped when nothing changed since
the last successful upload; "Back up now" always uploads. When an automatic upload fails (no
network, or the app's Drive access was revoked), the data stays on the device and the upload is
retried in the background until it succeeds.

If the Google account is removed from the device's system settings, the destination row is kept but
marked "Account not on this device" and backups pause; adding the account back, or using "Change
account" to pick another, resumes them, and "Remove account" discards the destination.

Tapping the Google Drive row offers: Back up now, Change frequency, Restore backup and Delete
backup (the last two only when a backup exists), Change account and Remove account. Remove account offers an optional "Also delete the Google Drive backup" checkbox when a
backup exists; removing the account never deletes the backup unless that is ticked.

"Restore backup" downloads the backup, which is handy after a reinstall or on a new
phone. When local sleeps exist, it asks whether to "Merge with existing data" or "Replace existing
data"; replacing deletes all local sleeps first. With no local sleeps the backup is restored
directly.

Setting this up for a self-built `gplay` flavor requires a Google Cloud project of your own: enable
the Drive API, add the `.../auth/drive.appdata` scope to the OAuth consent screen, and create an
OAuth 2.0 client ID of type "Android" for the app's package name and signing certificate SHA-1
fingerprint (register both the release package and the `.debug` package if you test debug builds).
The `drive.appdata` scope is not "sensitive", so no Google verification review is needed. Sign-in
fails until this is configured.

### Dashboard

You can also customize the dashboard duration, which limits the sleeps and sleep statistics on the
dashboard and graphs to the time period selected in the main activity. The default is to only show
the past week.

There is also an option to define your ideal sleep length, which is used for some of the graphs (see
Graphs activity below).

The other setting influencing the sleep stats is a sleep start delay. Assuming that one presses
start, followed by 8 hours, then stop, in case a sleep delay of 15 minutes is set, the recorded
sleep length will be 7:45, not 8:00, by increasing the sleep start timestamp.

The 'Show average of sleeps duration' setting is disabled by default and is useful if you always
sleep once a day, but sometimes you forget to track your sleep, still you're interested in the
average of your sleeps.

The 'Show average of daily sums' setting is enabled by default and is useful if you always track
your sleeps, but you may sleep multiple times a day. This will first count the sum of your sleeps
within a day, and count the average of those sums.

The 'Ignore empty days when showing average of daily sums' setting is enabled by default and ignores
empty days when counting the average of daily sums, assuming that you probably just forgot to track
your sleep(s) on that day. If this is not the case and you in fact sometimes skip an entire day,
then disable this setting.

The 'Use median instead of average when counting the daily duration' setting is disabled by default
and uses median instead of average when showing a single duration for the daily sum of several days.
This is less expected, but can be useful in case you filter out e.g. sick days where one may
undersleep or oversleep.

### Past sleeps

The past sleeps section allow configuring the contents of the individual sleep cards:

- awake time is hidden by default

- the read-only rating is hidden by default on the main activity, the read-write rating is always
  visible in the sleep activity

The sleep cards are not re-created when changing settings, so you need to restart plees-tracker to
see the effect.

### Do not disturb when tracking

This option enables automatic activation of DND (Do Not Disturb) mode when you start tracking your sleep. When toggled on for the first time, you will be prompted to grant Plees Tracker permissions to modify DND settings.

Upon ceasing sleep tracking, the DND setting you had enabled prior to initiating the tracking will be restored.

## Sleep activity

The sleep activity allows modifying the start,  stop time or rating of a single recorded sleep,
which is useful if you want to update the recorded timestamps to better match reality.

You can also take a multi-line plain text note for the sleep there.

## Stats activity

The main activity considers all sleeps for the selected duration when counting the sleeps or when
calculating the 2 kind of averages for your sleeps. The stats activity provides the same stats for
all possible durations, specifically:

- last week

- last two weeks

- last month

- last year

- all time

## Graphs activity

The graphs activity provides an alternative way to analyze your sleep data. Currently, the following
graphs are provided (select the graph via the menu in the upper right):

- Deficit/surplus: This graph shows the difference between the ideal sleep length (as customized in
  settings) versus the actual hours slept per day - positive is surplus, negative is deficit - along
  with a cumulative total.

- Length: This graph shows the hours slept per day, along with a cumulative moving average.

- Start time: This graph shows the start time of the first sleep per day (where day is based on the
  date of the sleep's ending time), along with a cumulative moving average.

- Rating: This graph shows the user-provided rating of the sleeps per day, along with a cumulative
  moving average. Note that no rating counts as 0.

- Variance: This graph shows the statistical variance and standard deviation of your daily sleep
  lengths. The more similar sleep lengths you get, the lower the variance will be. The variance
  units are hours squared and the standard deviation units are hours.

The graphs are generated based on sleeps within the selected dashboard duration.

## Credits

Icons made by [Dave Gandy](https://www.flaticon.com/authors/dave-gandy) and
[Freepik](https://www.flaticon.com/authors/freepik) from
[Flaticon](https://www.flaticon.com/).
