/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView

import com.google.android.material.floatingactionbutton.FloatingActionButton

import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val applicationContext = applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val dataModel = DataModel.dataModel
        dataModel.init(applicationContext, preferences)

        val sleepsAdapter = SleepsAdapter()
        dataModel.sleepsLive.observe(this, Observer { sleeps ->
            if (sleeps != null) {
                val countStat = findViewById<TextView>(R.id.count_stat)
                countStat.text = DataModel.getSleepCountStat(sleeps)
                val durationStat = findViewById<TextView>(R.id.duration_stat)
                durationStat.text = DataModel.getSleepDurationStat(sleeps)
                sleepsAdapter.data = sleeps
            }
        })

        val recyclerView = findViewById<RecyclerView>(R.id.sleeps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = sleepsAdapter
        sleepsAdapter.registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(positionStart: Int,
                                                     itemCount: Int) {
                        recyclerView.scrollToPosition(positionStart)
                    }
                })
        val itemTouchHelper = ItemTouchHelper(SleepTouchCallback(sleepsAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)

        updateView()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MainService::class.java)
        stopService(intent)
    }

    override fun onStop() {
        super.onStop()
        val intent = Intent(this, MainService::class.java)
        val dataModel = DataModel.dataModel
        if (dataModel.start != null && dataModel.stop == null) {
            startService(intent)
        }
    }

    // Used from layout XML.
    fun startStop(v: View) {
        val dataModel = DataModel.dataModel
        if (dataModel.start != null && dataModel.stop == null) {
            dataModel.stop = Calendar.getInstance().time
            dataModel.storeSleep()
        } else {
            dataModel.start = Calendar.getInstance().time
            dataModel.stop = null
        }
        updateView()
    }

    private fun exportData() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_TITLE, "plees-tracker.csv")
        startActivityForResult(intent, EXPORT_CODE)
    }

    private fun importData() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "text/csv"
        startActivityForResult(intent, IMPORT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int,
                                  data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val cr = contentResolver
        val uri = data?.data
        if (uri == null) {
            Log.e(TAG, "onActivityResult: null url")
            return
        }

        if (requestCode == EXPORT_CODE) {
            try {
                cr.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.e(
                        TAG,
                        "onActivityResult: takePersistableUriPermission() failed for write")
                return
            }

            var os: OutputStream? = null
            try {
                os = cr.openOutputStream(uri)
                if (os == null) {
                    Log.e(TAG, "onActivityResult: openOutputStream() failed")
                    return
                }
                val dataModel = DataModel.dataModel
                dataModel.exportData(os)
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: write() failed")
            } finally {
                if (os != null) {
                    try {
                        os.close()
                    } catch (e: RuntimeException) {
                        throw e
                    } catch (e: Exception) {
                    }

                }
            }
        } else if (requestCode == IMPORT_CODE) {
            var `is`: InputStream? = null
            try {
                `is` = cr.openInputStream(uri)
                val dataModel = DataModel.dataModel
                dataModel.importData(`is`!!)
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: read() failed")
                return
            } finally {
                if (`is` != null) {
                    try {
                        `is`.close()
                    } catch (e: RuntimeException) {
                        throw e
                    } catch (e: Exception) {
                    }

                }
            }
            updateView()
        }
    }

    private fun updateView() {
        val dataModel = DataModel.dataModel
        val status = findViewById<TextView>(R.id.status)
        val startStop = findViewById<FloatingActionButton>(R.id.start_stop)

        if (dataModel.start != null && dataModel.stop != null) {
            status.text = R.string.tracking_stopped.toString()
            startStop.contentDescription = getString(R.string.start_again)
            startStop.setImageResource(R.drawable.ic_start)
        } else if (dataModel.start != null) {
            status.text = String.format(
                    getString(R.string.sleeping_since),
                    DataModel.formatTimestamp(dataModel.start!!))
            startStop.contentDescription = getString(R.string.stop)
            startStop.setImageResource(R.drawable.ic_stop)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.import_data -> {
                importData()
                return true
            }
            R.id.export_data -> {
                exportData()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = "MainActivity"
        private val IMPORT_CODE = 1
        private val EXPORT_CODE = 2
    }
}
