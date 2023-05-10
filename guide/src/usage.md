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

Backup settings allow you to automatically back up your sleeps after a tracking stopped. This is
useful in case you selected a path which is then implicitly synchronized to some external server,
e.g. Nextcloud.

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

### Past sleeps

The past sleeps section allow configuring the contents of the individual sleep cards:

- awake time is hidden by default

- the read-only rating is hidden by default on the main activity, the read-write rating is always
  visible in the sleep activity

The sleep cards are not re-created when changing settings, so you need to restart plees-tracker to
see the effect.

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

The graphs are generated based on sleeps within the selected dashboard duration.

## Credits

Icons made by [Dave Gandy](https://www.flaticon.com/authors/dave-gandy) and
[Freepik](https://www.flaticon.com/authors/freepik) from
[Flaticon](https://www.flaticon.com/).
