/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import java.util.Calendar
import kotlinx.android.synthetic.main.activity_main.average_linear_layout
import kotlinx.android.synthetic.main.activity_main.daily_linear_layout
import kotlinx.android.synthetic.main.activity_main.nighs_linear_layout

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferenceListener = SharedPreferencesChangeListener()
        sharedPreferenceListener.applyTheme(PreferenceManager.getDefaultSharedPreferences(this))

        viewModel = ViewModelProvider.NewInstanceFactory().create(MainViewModel::class.java)

        setContentView(R.layout.activity_main)
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        DataModel.init(applicationContext, preferences)

        val sleepsAdapter = SleepsAdapter()
        DataModel.sleepsLive.observe(this, Observer { sleeps ->
            if (sleeps != null) {
                val countStat = findViewById<TextView>(R.id.count_stat)
                countStat.text = DataModel.getSleepCountStat(sleeps)
                val durationStat = findViewById<TextView>(R.id.duration_stat)
                durationStat.text = DataModel.getSleepDurationStat(sleeps)
                val durationDailyStat = findViewById<TextView>(R.id.duration_daily_stat)
                durationDailyStat.text = DataModel.getSleepDurationDailyStat(sleeps)
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
                    override fun onItemRangeInserted(
                        positionStart: Int,
                        itemCount: Int
                    ) {
                        recyclerView.scrollToPosition(positionStart)
                    }
                })
        val itemTouchHelper = ItemTouchHelper(SleepTouchCallback(applicationContext,
                viewModel, sleepsAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        val sleepClickCallback = SleepClickCallback(this, sleepsAdapter, recyclerView)
        sleepsAdapter.clickCallback = sleepClickCallback

        updateView()
        cornerRadius()
        addCorners()
    }

    private fun addCorners() {
        // setting corners for linear dashboard

        // night dashboard linear layout
        nighs_linear_layout.background = cornerRadius()
        val nightsColor = ContextCompat.getColor(this, R.color.dash_nights)
        var filter = PorterDuffColorFilter(nightsColor, PorterDuff.Mode.SRC_OVER)
        nighs_linear_layout.background.colorFilter = filter

        // average dashboard linear layout
        average_linear_layout.background = cornerRadius()
        val averageColor = ContextCompat.getColor(this, R.color.dash_average)
        filter = PorterDuffColorFilter(averageColor, PorterDuff.Mode.SRC_OVER)
        average_linear_layout.background.colorFilter = filter

        // daily dashboard linear layout
        daily_linear_layout.background = cornerRadius()
        val dailyAverageColor = ContextCompat.getColor(this, R.color.dash_daily)
        filter = PorterDuffColorFilter(dailyAverageColor, PorterDuff.Mode.SRC_OVER)
        daily_linear_layout.background.colorFilter = filter
    }

    private fun cornerRadius(): GradientDrawable {
        val gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadii = floatArrayOf(12f, 12f, 12f, 12f, 12f, 12f, 12f, 12f)
        return gradientDrawable
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MainService::class.java)
        stopService(intent)
    }

    override fun onStop() {
        super.onStop()
        val intent = Intent(this, MainService::class.java)
        if (DataModel.start != null && DataModel.stop == null) {
            startService(intent)
        }
    }

    // Used from layout XML.
    fun startStop(@Suppress("UNUSED_PARAMETER") v: View) {
        if (DataModel.start != null && DataModel.stop == null) {
            DataModel.stop = Calendar.getInstance().time
            viewModel.stopSleep()
        } else {
            DataModel.start = Calendar.getInstance().time
            DataModel.stop = null
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        val cr = contentResolver
        val uri = data?.data
        if (uri == null) {
            Log.e(TAG, "onActivityResult: null url")
            return
        }

        if (requestCode == EXPORT_CODE) {
            try {
                viewModel.exportData(applicationContext, cr, uri)
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: exportData() failed")
            }
        } else if (requestCode == IMPORT_CODE) {
            try {
                viewModel.importData(applicationContext, cr, uri)
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: importData() failed")
                return
            }
            updateView()
        }
    }

    private fun createColorStateList(color: Int): ColorStateList {
        return ColorStateList.valueOf(ContextCompat.getColor(this, color))
    }

    private fun updateView() {
        val status = findViewById<TextView>(R.id.status)
        val startStop = findViewById<FloatingActionButton>(R.id.start_stop)

        if (DataModel.start != null && DataModel.stop != null) {
            status.text = getString(R.string.tracking_stopped)
            startStop.contentDescription = getString(R.string.start_again)
            startStop.setImageResource(R.drawable.ic_start)

            // Set to custom, ~blue.
            startStop.backgroundTintList = createColorStateList(R.color.colorPrimary)

            return
        }
        DataModel.start?.let { start ->
            status.text = String.format(getString(R.string.sleeping_since),
                    DataModel.formatTimestamp(start))
            startStop.contentDescription = getString(R.string.stop)
            startStop.setImageResource(R.drawable.ic_stop)

            // Back to default, ~red.
            startStop.backgroundTintList = createColorStateList(R.color.colorAccent)

            return
        }

        // Set to custom, ~blue.
        startStop.backgroundTintList = createColorStateList(R.color.colorPrimary)
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
            R.id.share -> {
                val action = getString(R.string.share)
                val title = getString(R.string.app_name)
                val deepLink = getString(R.string.website_link)
                val text = getString(R.string.share_app_text, deepLink)
                share(action, title, text)
                return true
            }
            R.id.about -> {
                LibsBuilder()
                        .withActivityTitle(getString(R.string.about_toolbar))
                        .withAboutAppName(getString(R.string.app_name))
                        .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                        .withAboutDescription(getString(R.string.app_description))
                        .start(this)
                return true
            }
            R.id.website -> {
                val website = getString(R.string.website_link)
                open(Uri.parse(website))
                return true
            }
            R.id.settings -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun share(action: String, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, title)
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        startActivity(Intent.createChooser(intent, action))
    }

    private fun open(link: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, link)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val IMPORT_CODE = 1
        private const val EXPORT_CODE = 2
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
