/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    private static final int IMPORT_CODE = 1;
    private static final int EXPORT_CODE = 2;

    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DataModel dataModel = DataModel.getDataModel();
        dataModel.init(getApplicationContext());

        // Restore start timestamp in case the notification outlived the app.
        if (dataModel.getStart() == null)
        {
            Intent intent = getIntent();
            if (intent.hasExtra("start"))
            {
                Date start = new Date(intent.getLongExtra("start", 0));
                dataModel.setStart(start);
            }
        }

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

    // Used from layout XML.
    @SuppressWarnings("unused") public void startStop(View v)
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

    private void exportData()
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "plees-tracker.csv");
        startActivityForResult(intent, EXPORT_CODE);
    }

    private void importData()
    {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/csv");
        startActivityForResult(intent, IMPORT_CODE);
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

        if (requestCode == EXPORT_CODE)
        {
            try
            {
                cr.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
            catch (SecurityException e)
            {
                Log.e(
                    TAG,
                    "onActivityResult: takePersistableUriPermission() failed for write");
                return;
            }

            OutputStream os = null;
            try
            {
                os = cr.openOutputStream(uri);
                DataModel dataModel = DataModel.getDataModel();
                dataModel.exportData(os);
            }
            catch (Exception e)
            {
                Log.e(TAG, "onActivityResult: write() failed");
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
        else if (requestCode == IMPORT_CODE)
        {
            InputStream is = null;
            try
            {
                is = cr.openInputStream(uri);
                DataModel dataModel = DataModel.getDataModel();
                dataModel.importData(is);
            }
            catch (Exception e)
            {
                Log.e(TAG, "onActivityResult: read() failed");
                return;
            }
            finally
            {
                if (is != null)
                {
                    try
                    {
                        is.close();
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
            updateView();
        }
    }

    private void updateView()
    {
        DataModel dataModel = DataModel.getDataModel();
        TextView status = findViewById(R.id.status);
        TextView start = findViewById(R.id.start);
        TextView stop = findViewById(R.id.stop);
        TextView countStat = findViewById(R.id.count_stat);
        TextView durationStat = findViewById(R.id.duration_stat);
        FloatingActionButton startStop = findViewById(R.id.start_stop);
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
            startStop.setContentDescription(getString(R.string.start_again));
            startStop.setImageResource(R.drawable.ic_start);
        }
        else if (dataModel.getStart() != null)
        {
            status.setText(R.string.tracking);
            start.setText(sdf.format(dataModel.getStart()));
            stop.setText("");
            startStop.setContentDescription(getString(R.string.stop));
            startStop.setImageResource(R.drawable.ic_stop);
        }
        countStat.setText(dataModel.getSleepCountStat());
        durationStat.setText(dataModel.getSleepDurationStat());
    }

    @Override public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        switch (item.getItemId())
        {
        case R.id.import_data:
            importData();
            return true;
        case R.id.export_data:
            exportData();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
