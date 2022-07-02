/*
 * Copyright 2022 ELY M. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import java.util.Calendar
import kotlin.math.sqrt

@Suppress("DEPRECATION")
class SleepService : Service(), SensorEventListener {

    private lateinit var viewModel: MainViewModel
    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var mAccel = 0f
    private var mAccelCurrent = 0f
    private var mAccelLast = 0f

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager!!.registerListener(
            this, mAccelerometer, SensorManager.SENSOR_DELAY_UI, Handler()
        )
        return START_STICKY
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        viewModel = ViewModelProvider.AndroidViewModelFactory(application)
            .create(MainViewModel::class.java)

        val x: Float = event.values.get(0)
        val y: Float = event.values.get(1)
        val z: Float = event.values.get(2)
        mAccelLast = mAccelCurrent
        mAccelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = mAccelCurrent - mAccelLast
        mAccel = mAccel * 0.9f + delta // perform low-cut filter
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val sleeptrack = preferences.getBoolean("sleeptrack", false)
        val sensitive = preferences.getString("phone_movement_weight", "6.3")
        val sensitivef = sensitive!!.toFloat()
        if (mAccel > sensitivef) {
            if (sleeptrack) {
                Log.i("pleestracker", "SleepService: phone movement: " + mAccel.toString())
                val editor = DataModel.preferences.edit()
                editor.putBoolean("sleeptrack", false)
                editor.apply()
                Log.i("pleestracker", "SleepService: sleeptrack after phone moved: " + sleeptrack)
                // need to send stop time to database and do stop process
                DataModel.stop = Calendar.getInstance().time
                viewModel.stopSleep(applicationContext, contentResolver)
                Log.i("pleestracker", "sleep tracking should stop")
            }
        }
    }
}
