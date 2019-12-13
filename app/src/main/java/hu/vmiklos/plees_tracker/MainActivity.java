package hu.vmiklos.plees_tracker;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
{
    private Date mStart = null;
    private Date mStop = null;

    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null)
        {
            long start = savedInstanceState.getLong("mStart");
            if (start != 0)
            {
                mStart = new Date(start);
            }
            long stop = savedInstanceState.getLong("mStop");
            if (stop != 0)
            {
                mStop = new Date(stop);
            }
        }
        updateState();
    }

    @Override protected void onSaveInstanceState(Bundle outState)
    {
        long start = 0;
        if (mStart != null)
        {
            start = mStart.getTime();
        }
        outState.putLong("mStart", start);
        long stop = 0;
        if (mStop != null)
        {
            stop = mStop.getTime();
        }
        outState.putLong("mStop", stop);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStart()
    {
        super.onStart();
        Log.d("plees", "onStart");
    }

    @Override protected void onStop()
    {
        super.onStop();
        Log.d("plees", "onStop");
    }

    public void startStop(View v)
    {
        if (mStart != null && mStop == null)
        {
            mStop = Calendar.getInstance().getTime();
        }
        else
        {
            mStart = Calendar.getInstance().getTime();
            mStop = null;
        }
        updateState();
    }

    private void updateState()
    {
        TextView state = (TextView)findViewById(R.id.state);
        Button startStop = (Button)findViewById(R.id.startStop);
        SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        if (mStart != null && mStop != null)
        {
            long durationMS = mStop.getTime() - mStart.getTime();
            String duration = formatDuration(durationMS / 1000);
            state.setText("Started on " + sdf.format(mStart) + ", stopped on " +
                          sdf.format(mStop) + ", slept for " + duration + ".");
            startStop.setText("Start again");
        }
        else if (mStart != null)
        {
            state.setText("Started on " + sdf.format(mStart) + ", tracking.");
            startStop.setText("Stop");
        }
        else
        {
            state.setText("Press start to begin tracking.");
            startStop.setText("Start");
        }
    }

    private static String formatDuration(long seconds)
    {
        return String.format("%d:%02d:%02d", seconds / 3600,
                             (seconds % 3600) / 60, seconds % 60);
    }
}
