#!/usr/bin/env python

"""
Simple script to convert Sleep as Android export CSV into Plees CSV.

Usage:
> ./sleep-as-android.py
Input file [sleep-export.csv]:
Output file [plees-convert.csv]:
# ... a bunch of parse errors - these are expected ...
imported 2341 rows from sleep-export.csv
exported data to plees-convert.csv
"""

from collections import namedtuple
import csv
import datetime
import zoneinfo

DEFAULT_INPUT = 'sleep-export.csv'
DEFAULT_OUTPUT = 'plees-convert.csv'
DT_FORMAT = '%d. %m. %Y %H:%M'
PleesRow = namedtuple('PleesRow', ['start', 'stop', 'rating'])

def parse_tz(tz_str):
    return zoneinfo.ZoneInfo(tz_str)

def parse_datetime(dt_str, tz):
    dt = datetime.datetime.strptime(dt_str, DT_FORMAT).astimezone(tz)
    return int(dt.timestamp() * 1000)

def import_sleep(filename):
    data = []
    with open(filename) as sleepfile:
        reader = csv.reader(sleepfile)
        for count, row in enumerate(reader):
            # attempt to parse row: newer entries follow header row followed by
            # data row, but older entries are not consistent so just brute-forcing
            try:
                tz = parse_tz(row[1])
                start = parse_datetime(row[2], tz)
                stop = parse_datetime(row[3], tz)
                rating = int(float(row[6]))
                data.append(PleesRow(start, stop, rating))
            except Exception as e:
                print(f'parse error, row {count} ({e.__class__})')
    return data

def export_plees(filename, data):
    with open(filename, 'w', newline='') as pleesfile:
        writer = csv.writer(pleesfile)
        writer.writerow(['sid', 'start', 'stop', 'rating']) # header row
        # sleep is in descending order, so reversing for ids
        for sid, row in enumerate(reversed(data), 1):
            writer.writerow([sid, row.start, row.stop, row.rating])

if __name__ == "__main__":
    sleep_filename = input(f'Input file [{DEFAULT_INPUT}]: ') or DEFAULT_INPUT
    plees_filename = input(f'Output file [{DEFAULT_OUTPUT}]: ') or DEFAULT_OUTPUT

    data = import_sleep(sleep_filename)
    print(f'imported {len(data)} rows from {sleep_filename}')

    export_plees(plees_filename, data)
    print(f'exported data to {plees_filename}')
