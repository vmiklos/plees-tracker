/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    private static final int CODE_EXPORT = 1;

    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DataModel dataModel = DataModel.getDataModel();
        dataModel.setContext(getApplicationContext());
        updateView();
    }

    @Override protected void onStart()
    {
        super.onStart();
        Intent intent = new Intent(this, MainService.class);
        stopService(intent);
    }

    @Override protected void onStop()
    {
        super.onStop();
        Intent intent = new Intent(this, MainService.class);
        DataModel dataModel = DataModel.getDataModel();
        if (dataModel.getStart() != null && dataModel.getStop() == null)
        {
            startService(intent);
        }
    }

    public void startStop(View v)
    {
        DataModel dataModel = DataModel.getDataModel();
        if (dataModel.getStart() != null && dataModel.getStop() == null)
        {
            dataModel.setStop(Calendar.getInstance().getTime());
            dataModel.storeSleep();
        }
        else
        {
            dataModel.setStart(Calendar.getInstance().getTime());
            dataModel.setStop(null);
        }
        updateView();
    }

    public void export(View v)
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "plees-tracker.csv");
        startActivityForResult(intent, CODE_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        final ContentResolver cr = getContentResolver();
        final Uri uri = data != null ? data.getData() : null;
        if (uri == null)
        {
            Log.e(TAG, "onActivityResult: null url");
            return;
        }
        try
        {
            cr.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        catch (SecurityException e)
        {
            Log.e(TAG,
                  "onActivityResult: takePersistableUriPermission() failed");
            return;
        }

        OutputStream os = null;
        try
        {
            os = cr.openOutputStream(uri);
            DataModel dataModel = DataModel.getDataModel();
            dataModel.export(os);
        }
        catch (Exception e)
        {
            Log.e(TAG, "onActivityResult: write() failed");
            return;
        }
        finally
        {
            if (os != null)
            {
                try
                {
                    os.close();
                }
                catch (RuntimeException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    private void updateView()
    {
        DataModel dataModel = DataModel.getDataModel();
        TextView status = (TextView)findViewById(R.id.status);
        TextView start = (TextView)findViewById(R.id.start);
        TextView stop = (TextView)findViewById(R.id.stop);
        TextView countStat = (TextView)findViewById(R.id.count_stat);
        TextView durationStat = (TextView)findViewById(R.id.duration_stat);
        Button startStop = (Button)findViewById(R.id.startStop);
        SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        if (dataModel.getStart() != null && dataModel.getStop() != null)
        {
            long durationMS =
                dataModel.getStop().getTime() - dataModel.getStart().getTime();
            String duration = DataModel.formatDuration(durationMS / 1000);
            status.setText(
                String.format(getString(R.string.slept_for), duration));
            start.setText(sdf.format(dataModel.getStart()));
            stop.setText(sdf.format(dataModel.getStop()));
            startStop.setText(R.string.start_again);
        }
        else if (dataModel.getStart() != null)
        {
            status.setText(R.string.tracking);
            start.setText(sdf.format(dataModel.getStart()));
            stop.setText("");
            startStop.setText(R.string.stop);
        }
        countStat.setText(dataModel.getSleepCountStat() + " nights");
        durationStat.setText("Average duration is " +
                             dataModel.getSleepDurationStat());
    }
}
