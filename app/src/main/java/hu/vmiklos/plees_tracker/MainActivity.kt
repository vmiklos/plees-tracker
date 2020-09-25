/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    /**
     * Determines if x,y hits view or not.
     */
    private fun hitTest(view: View, x: Float, y: Float): Boolean {
        val translationX = view.translationX
        val translationY = view.translationY
        if (x < view.left + translationX) {
            return false
        }
        if (x > view.right + translationX) {
            return false
        }
        if (y < view.top + translationY) {
            return false
        }
        if (y > view.bottom + translationY) {
            return false
        }

        return true
    }

    /**
     * If there is a rating bar inside this recycler view, get that.
     */
    private fun findRatingBar(rv: RecyclerView, e: MotionEvent): View? {
        val sleepItem = rv.findChildViewUnder(e.x, e.y) ?: return null
        val ratingBar = sleepItem.findViewById<View>(R.id.sleep_item_rating)
        if (hitTest(ratingBar, e.x, e.y)) {
            return ratingBar
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferenceListener = SharedPreferencesChangeListener()
        sharedPreferenceListener.applyTheme(PreferenceManager.getDefaultSharedPreferences(this))

        viewModel = ViewModelProvider.NewInstanceFactory().create(MainViewModel::class.java)

        setContentView(R.layout.activity_main)
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        DataModel.init(applicationContext, preferences)

        val sleepsAdapter = SleepsAdapter(viewModel)
        DataModel.sleepsLive.observe(this, { sleeps ->
            if (sleeps != null) {
                val fragments = supportFragmentManager
                val stats = fragments.findFragmentById(R.id.dashboard_body)?.view
                val countStat = stats?.findViewById<TextView>(R.id.fragment_stats_sleeps)
                countStat?.text = DataModel.getSleepCountStat(sleeps)
                val durationStat = stats?.findViewById<TextView>(R.id.fragment_stats_average)
                durationStat?.text = DataModel.getSleepDurationStat(sleeps)
                val durationDailyStat = stats?.findViewById<TextView>(R.id.fragment_stats_daily)
                durationDailyStat?.text = DataModel.getSleepDurationDailyStat(sleeps)
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

        // Swipe on the rating bar goes to the rating bar itself.
        recyclerView.addOnItemTouchListener(
                object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        return findRatingBar(rv, e) != null
                    }

                    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                        val ratingBar = findRatingBar(rv, e) ?: return
                        val x = e.x - (ratingBar.left + ratingBar.translationX)
                        val y = e.y - (ratingBar.top + ratingBar.translationY)
                        e.setLocation(x, y)
                        ratingBar.onTouchEvent(e)
                    }
                }
        )

        // Otherwise swipe on a card view deletes it.
        val itemTouchHelper = ItemTouchHelper(SleepTouchCallback(applicationContext,
                viewModel, sleepsAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
        val sleepClickCallback = SleepClickCallback(this, sleepsAdapter, recyclerView)
        sleepsAdapter.clickCallback = sleepClickCallback

        // Hide label of FAB on scroll.
        val fabText = findViewById<TextView>(R.id.start_stop_text)
        val listener = View.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                fabText.visibility = View.GONE
            } else {
                fabText.visibility = View.VISIBLE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            recyclerView.setOnScrollChangeListener(listener)
        }

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
        intent.type = "text/*"
        val mimeTypes = arrayOf("text/csv", "text/comma-separated-values")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
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
        val startStopLayout = findViewById<LinearLayout>(R.id.start_stop_layout)
        val startStop = findViewById<FloatingActionButton>(R.id.start_stop)
        val startStopText = findViewById<TextView>(R.id.start_stop_text)

        if (DataModel.start != null && DataModel.stop != null) {
            status.text = getString(R.string.tracking_stopped)
            startStop.contentDescription = getString(R.string.start_again)
            startStop.setImageResource(R.drawable.ic_start)
            startStopText.text = getString(R.string.start)

            // Set to custom, ~blue.
            startStop.backgroundTintList = createColorStateList(R.color.colorFabPrimary)
            startStopLayout.backgroundTintList = startStop.backgroundTintList

            return
        }
        DataModel.start?.let { start ->
            status.text = String.format(getString(R.string.sleeping_since),
                    DataModel.formatTimestamp(start))
            startStop.contentDescription = getString(R.string.stop)
            startStop.setImageResource(R.drawable.ic_stop)
            startStopText.text = getString(R.string.stop)

            // Back to default, ~red.
            startStop.backgroundTintList = createColorStateList(R.color.colorFabAccent)
            startStopLayout.backgroundTintList = startStop.backgroundTintList

            return
        }

        // Set to custom, ~blue.
        startStop.backgroundTintList = createColorStateList(R.color.colorFabPrimary)
        startStopLayout.backgroundTintList = startStop.backgroundTintList
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
            R.id.stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
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
