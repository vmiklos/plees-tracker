/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import hu.vmiklos.plees_tracker.calendar.CalendarImport
import hu.vmiklos.plees_tracker.calendar.UserCalendar
import java.util.Calendar

/**
 * The activity is the primary UI of the app: allows starting and stopping the
 * tracking.
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var viewModel: MainViewModel

    // SharedPreferences keeps listeners in a WeakHashMap, so keep this as a member.
    private val sharedPreferenceListener = SharedPreferencesChangeListener()

    private val exportPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        checkCalendarPermissionGranted(permissions, ::exportCalendarData)
    }

    private val importPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        checkCalendarPermissionGranted(permissions, ::importCalendarData)
    }

    private val importActivityResult =
        registerForActivityResult(StartActivityForResult()) { result ->
            try {
                result.data?.data?.let { uri ->
                    viewModel.importData(applicationContext, contentResolver, uri)
                    updateView()
                }
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: importData() failed")
            }
        }

    private val exportActivityResult =
        registerForActivityResult(StartActivityForResult()) { result ->
            try {
                result.data?.data?.let { uri ->
                    viewModel.exportDataToFile(
                        applicationContext, contentResolver, uri, showToast = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "onActivityResult: exportData() failed")
            }
        }

    private fun setDashboardText(durationStr: String) {
        var index = resources.getStringArray(R.array.duration_entry_values).indexOf(durationStr)
        val durations = resources.getStringArray(R.array.duration_entries)
        if (index == -1) {
            index = durations.size - 1 // indexOf may return -1, which will out of bounds.
        }
        val durationHeaderStr = resources.getStringArray(R.array.duration_entries)[index]
        findViewById<TextView>(R.id.dashboard_header)?.text =
            getString(R.string.dashboard, durationHeaderStr)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.getBooleanExtra("startStop", false)) {
                onClick(findViewById(R.id.start_stop_layout))
                it.removeExtra("startStop")
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferenceListener.applyTheme(PreferenceManager.getDefaultSharedPreferences(this))

        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        preferences.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)
        DataModel.init(applicationContext, preferences)

        viewModel = ViewModelProvider.AndroidViewModelFactory(application)
            .create(MainViewModel::class.java)

        setContentView(R.layout.activity_main)
        val startStop = findViewById<LinearLayout>(R.id.start_stop_layout)
        startStop.setOnClickListener(this)

        preferences.liveData("dashboard_duration", "-7").observe(
            this
        ) { setDashboardText(it ?: "0") }

        // Hide plain avg and avg(daily sum) if requested:
        preferences.liveDataBoolean("show_average_sleep_durations", false).observe(
            this
        ) {
            it?.let {
                val fragments = supportFragmentManager
                val stats = fragments.findFragmentById(R.id.dashboard_body)?.view
                val layout = stats?.findViewById<LinearLayout>(R.id.fragment_stats_average_layout)
                if (it) {
                    layout?.visibility = View.VISIBLE
                } else {
                    layout?.visibility = View.GONE
                }
            }
        }
        preferences.liveDataBoolean("show_average_daily_sums", true).observe(
            this
        ) {
            it?.let {
                val fragments = supportFragmentManager
                val stats = fragments.findFragmentById(R.id.dashboard_body)?.view
                val layout = stats?.findViewById<LinearLayout>(R.id.fragment_stats_daily_layout)
                if (it) {
                    layout?.visibility = View.VISIBLE
                } else {
                    layout?.visibility = View.GONE
                }
            }
        }

        val sleepsAdapter = SleepsAdapter(preferences)
        val recyclerView = findViewById<RecyclerView>(R.id.sleeps)
        viewModel.durationSleepsLive.observe(
            this
        ) { sleeps ->
            if (sleeps != null) {
                val fragments = supportFragmentManager
                val stats = fragments.findFragmentById(R.id.dashboard_body)?.view
                val countStat = stats?.findViewById<TextView>(R.id.fragment_stats_sleeps)
                countStat?.text = DataModel.getSleepCountStat(sleeps)
                val durationStat = stats?.findViewById<TextView>(R.id.fragment_stats_average)
                durationStat?.text = DataModel.getSleepDurationStat(
                    sleeps,
                    DataModel.getCompactView()
                )
                val durationDailyStat = stats?.findViewById<TextView>(R.id.fragment_stats_daily)
                durationDailyStat?.text = DataModel.getSleepDurationDailyStat(
                    sleeps,
                    DataModel.getCompactView(),
                    DataModel.getIgnoreEmptyDays()
                )
                sleepsAdapter.data = sleeps

                // Set up placeholder text if there are no sleeps.
                val noSleepsView = findViewById<TextView>(R.id.no_sleeps)
                if (sleeps.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    noSleepsView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    noSleepsView.visibility = View.GONE
                }
            }
        }

        val recyclerViewLayout = LinearLayoutManager(this)
        recyclerView.layoutManager = recyclerViewLayout
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = sleepsAdapter

        // Enable separators between sleep items.
        val dividerItemDecoration = DividerItemDecoration(
            recyclerView.context,
            recyclerViewLayout.orientation
        )
        recyclerView.addItemDecoration(dividerItemDecoration)

        sleepsAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(
                    positionStart: Int,
                    itemCount: Int
                ) {
                    recyclerView.scrollToPosition(positionStart)
                }
            })

        // Otherwise swipe on a card view deletes it.
        val itemTouchHelper = ItemTouchHelper(
            SleepTouchCallback(
                applicationContext,
                contentResolver,
                viewModel,
                sleepsAdapter
            )
        )
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
        recyclerView.setOnScrollChangeListener(listener)

        // See if the activity is triggered from the widget. If so, toggle the start/stop state.
        handleIntent(intent)

        updateView()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MainService::class.java)
        stopService(intent)
        val recyclerView = findViewById<RecyclerView>(R.id.sleeps)
        recyclerView.findViewHolderForAdapterPosition(0)?.let {
            // Since the adapter unconditionally gets assigned in `onCreate()`
            // it shouldn't be necessary to consider fixing it here.
            // If it is null at this point a lot more must have gone wrong as well.
            recyclerView.adapter?.onBindViewHolder(it, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        val intent = Intent(this, MainService::class.java)
        if (DataModel.start != null && DataModel.stop == null) {
            startService(intent)
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.start_stop_layout -> {
                if (DataModel.start != null && DataModel.stop == null) {
                    DataModel.stop = Calendar.getInstance().time
                    viewModel.stopSleep(applicationContext, contentResolver)
                } else {
                    DataModel.start = Calendar.getInstance().time
                    DataModel.stop = null
                }
                updateView()
            }
        }
    }

    private fun exportFileData() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_TITLE, "plees-tracker.csv")
        exportActivityResult.launch(intent)
    }

    private fun importFileData() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "text/*"
        val mimeTypes = arrayOf("text/csv", "text/comma-separated-values")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        importActivityResult.launch(intent)
    }

    private fun createColorStateList(color: Int): ColorStateList {
        return ColorStateList.valueOf(ContextCompat.getColor(this, color))
    }

    private fun updateView() {
        val status = findViewById<TextView>(R.id.status)
        val startStopLayout = findViewById<LinearLayout>(R.id.start_stop_layout)
        val startStop = findViewById<FloatingActionButton>(R.id.start_stop)
        val startStopText = findViewById<TextView>(R.id.start_stop_text)

        val dndManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val dndEnabled = preferences.getBoolean("enable_dnd", false)

        if (DataModel.start != null && DataModel.stop != null) {
            // When user stops tracking sleep
            if (dndManager.isNotificationPolicyAccessGranted and dndEnabled) {
                // Restore Do Not Disturb status when user started tracking
                val filterAll = NotificationManager.INTERRUPTION_FILTER_ALL
                dndManager.setInterruptionFilter(preferences.getInt("current_dnd", filterAll))
            } else {
                Log.w(TAG, "Failed to disable DND, permissions not enabled")
            }

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
            // When user starts tracking sleep
            preferences.edit()
                .putInt("current_dnd", dndManager.currentInterruptionFilter)
                .apply() // Saves current Do Not Disturb status
            if (dndManager.isNotificationPolicyAccessGranted and dndEnabled) {
                dndManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                Log.w(TAG, "Failed to enable DND, permissions not enabled")
            }
            status.text = String.format(
                getString(R.string.sleeping_since),
                DataModel.formatTimestamp(start, DataModel.getCompactView())
            )
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
            R.id.import_calendar_data -> {
                checkForCalendarPermission(importPermissionLauncher, ::importCalendarData)
                return true
            }
            R.id.import_file_data -> {
                importFileData()
                return true
            }
            R.id.export_file_data -> {
                exportFileData()
                return true
            }
            R.id.export_calendar_data -> {
                checkForCalendarPermission(exportPermissionLauncher, ::exportCalendarData)
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
            R.id.documentation -> {
                val website = getString(R.string.website_link)
                open(Uri.parse(website))
                return true
            }
            R.id.settings -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                return true
            }
            R.id.delete_all_sleep -> {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(getString(R.string.delete_all_message))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.delete_all_positive)) { _, _ ->
                        viewModel.deleteAllSleep()
                    }
                    .setNegativeButton(getString(R.string.delete_all_negative)) { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
                return true
            }
            R.id.stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                return true
            }
            R.id.graphs -> {
                startActivity(Intent(this, GraphsActivity::class.java))
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun checkCalendarPermissionGranted(
        granted: Map<String, Boolean>,
        onSuccess: (UserCalendar) -> Unit
    ) {
        // Check all permissions were granted
        if (granted.values.all { it }) {
            // Start picker for calendar
            showUserCalendarPicker(onSuccess)
        } else {
            // Permission denied
            Toast.makeText(
                this, getString(R.string.calendar_permission_required), Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkForCalendarPermission(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        block: (UserCalendar) -> Unit
    ) {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)) {
            PackageManager.PERMISSION_GRANTED -> showUserCalendarPicker(block)
            else -> {
                // Directly ask for the permissions
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            }
        }
    }

    private inline fun showUserCalendarPicker(crossinline block: (cal: UserCalendar) -> Unit) {

        // Fetch calendar data
        val calendars = CalendarImport.queryForCalendars(this)

        // No user calendars found, show dialog informing user
        if (calendars.isEmpty()) {
            showNoCalendarsFoundDialog()
            return
        }

        // Get name of calendar(s)
        val titles = calendars.map(UserCalendar::name).toTypedArray()
        var selectedItem = 0

        // Show User Calendar picker
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_calendar_dialog_title))
            .setNeutralButton(getString(R.string.select_calendar_dialog_negative), null)
            .setPositiveButton(getString(R.string.select_calendar_dialog_positive)) { _, _ ->
                block.invoke(calendars[selectedItem])
            }.setSingleChoiceItems(titles, selectedItem) { _, newSelection ->
                selectedItem = newSelection
            }.show()
    }

    private fun importCalendarData(selectedItem: UserCalendar) {
        viewModel.importDataFromCalendar(this, selectedItem.id)
    }

    private fun exportCalendarData(selectedItem: UserCalendar) {
        viewModel.exportDataToCalendar(this, selectedItem.id)
    }

    private fun showNoCalendarsFoundDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_calendar_dialog_title))
            .setMessage(getString(R.string.import_dialog_error_message))
            .setNegativeButton(getString(R.string.dismiss), null)
            .show()
    }

    private fun open(link: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, link)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
