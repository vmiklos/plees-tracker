/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Data model is the singleton shared state between the activity and the
 * service.
 */
public class DataModel
{
    private static final String TAG = "DataModel";
    private static final DataModel sDataModel = new DataModel();

    private Date mStart = null;
    private Date mStop = null;
    private Context mContext = null;
    private SharedPreferences mPreferences;
    private AppDatabase mDatabase = null;

    public static DataModel getDataModel() { return sDataModel; }

    private DataModel() {}

    void setStart(Date start)
    {
        mStart = start;

        // Save start timestamp in case the foreground service is killed.
        final SharedPreferences.Editor editor = mPreferences.edit();
        editor.putLong("start", mStart.getTime());
        editor.apply();
    }

    Date getStart() { return mStart; }

    void setStop(Date stop) { mStop = stop; }

    Date getStop() { return mStop; }

    void init(Context context, SharedPreferences preferences)
    {
        if (mContext != context)
        {
            mContext = context;
        }

        if (mPreferences != preferences)
        {
            mPreferences = preferences;
        }

        long start = mPreferences.getLong("start", 0);
        if (start > 0)
        {
            // Restore start timestamp in case the foreground service was
            // killed.
            mStart = new Date(start);
        }
    }

    private AppDatabase getDatabase()
    {
        if (mDatabase == null)
        {
            mDatabase =
                Room.databaseBuilder(mContext, AppDatabase.class, "database")
                    .allowMainThreadQueries()
                    .build();
        }
        return mDatabase;
    }

    void storeSleep()
    {
        Sleep sleep = new Sleep();
        sleep.start = mStart.getTime();
        sleep.stop = mStop.getTime();
        getDatabase().sleepDao().insert(sleep);

        // Drop start timestamp from preferences, it's in the database now.
        final SharedPreferences.Editor editor = mPreferences.edit();
        editor.remove("start");
        editor.apply();
    }

    LiveData<List<Sleep>> getSleepsLive()
    {
        return getDatabase().sleepDao().getAllLive();
    }

    void deleteSleeps() { getDatabase().sleepDao().deleteAll(); }

    String getSleepCountStat(List<Sleep> sleeps)
    {
        return String.valueOf(sleeps.size());
    }

    String getSleepDurationStat(List<Sleep> sleeps)
    {
        long sum = 0;
        for (Sleep sleep : sleeps)
        {
            long diff = sleep.stop - sleep.start;
            diff /= 1000;
            sum += diff;
        }
        int count = sleeps.size();
        if (count == 0)
        {
            return "";
        }
        return formatDuration(sum / count);
    }

    void importData(InputStream is)
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        try
        {
            boolean first = true;
            while ((line = br.readLine()) != null)
            {
                if (first)
                {
                    // Ignore the header.
                    first = false;
                    continue;
                }
                String[] cells = line.split(",");
                if (cells.length < 3)
                {
                    continue;
                }
                Sleep sleep = new Sleep();
                sleep.start = Long.valueOf(cells[1]);
                sleep.stop = Long.valueOf(cells[2]);
                getDatabase().sleepDao().insert(sleep);
            }
        }
        catch (IOException e)
        {
            Log.e(TAG, "importData: readLine() failed");
            return;
        }

        String text = mContext.getString(R.string.import_success);
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(mContext, text, duration);
        toast.show();
    }

    void exportData(OutputStream os)
    {
        try
        {
            List<Sleep> sleeps = getDatabase().sleepDao().getAll();
            os.write("sid,start,stop\n".getBytes());
            for (Sleep sleep : sleeps)
            {
                String row =
                    sleep.sid + "," + sleep.start + "," + sleep.stop + "\n";
                os.write(row.getBytes());
            }
        }
        catch (IOException e)
        {
            Log.e(TAG, "exportData: write() failed");
            return;
        }

        String text = mContext.getString(R.string.export_success);
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(mContext, text, duration);
        toast.show();
    }

    public static String formatDuration(long seconds)
    {
        return String.format(Locale.getDefault(), "%d:%02d:%02d",
                             seconds / 3600, (seconds % 3600) / 60,
                             seconds % 60);
    }

    public static String formatTimestamp(Date date)
    {
        SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }
}
