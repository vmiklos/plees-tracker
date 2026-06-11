/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.switchMap
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import hu.vmiklos.plees_tracker.calendar.CalendarExport
import hu.vmiklos.plees_tracker.calendar.CalendarImport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sleep ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sleep ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sleep ADD COLUMN wakes INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Data model is the singleton shared state between the activity and the
 * service.
 */
object DataModel {

    lateinit var preferences: SharedPreferences

    var preferencesActivity: PreferencesActivity? = null

    var start: Date? = null
        set(start) {
            field = start
            // Save start timestamp in case the foreground service is killed.
            preferences.edit {
                field?.let {
                    putLong("start", it.time)
                }
            }
        }

    var stop: Date? = null

    lateinit var database: AppDatabase

    val sleepsLive: LiveData<List<Sleep>>
        get() = database.sleepDao().getAllLive()

    private var initialized: Boolean = false

    fun init(context: Context, preferences: SharedPreferences) {
        if (initialized) {
            return
        }

        this.preferences = preferences

        val start = preferences.getLong("start", 0)
        if (start > 0) {
            // Restore start timestamp in case the foreground service was
            // killed.
            this.start = Date(start)
        }
        database = Room.databaseBuilder(context, AppDatabase::class.java, "database")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .build()
        initialized = true
        migrateFromSingleDestination()
    }

    /** Returns all currently configured backup destinations in insertion order. */
    fun getDestinations(): List<BackupDestination> =
        BackupDestination.listFromJson(preferences.getString("backup_destinations", "[]") ?: "[]")

    private fun saveDestinations(destinations: List<BackupDestination>) {
        preferences.edit {
            putString("backup_destinations", BackupDestination.listToJson(destinations))
        }
    }

    fun addDestination(destination: BackupDestination) {
        saveDestinations(getDestinations() + destination)
    }

    fun removeDestination(destination: BackupDestination) {
        saveDestinations(getDestinations().filter { it != destination })
    }

    /** Swaps [old] for [new], keeping its position in the list. */
    fun replaceDestination(old: BackupDestination, new: BackupDestination) {
        saveDestinations(getDestinations().map { if (it == old) new else it })
    }

    /**
     * One-time migration from the old single-destination SharedPreferences keys (auto_backup,
     * auto_backup_path) to the new backup_destinations JSON list. Runs only when the old keys
     * are present and the new key is absent.
     */
    private fun migrateFromSingleDestination() {
        if (preferences.contains("backup_destinations") || !preferences.contains("auto_backup")) {
            return
        }
        val destinations = BackupDestination.fromLegacyPreferences(
            autoBackup = preferences.getBoolean("auto_backup", false),
            folderPath = preferences.getString("auto_backup_path", null)
        )
        saveDestinations(destinations)
        preferences.edit {
            // Keep backups running for users who had the legacy switch on; the new key
            // defaults to off otherwise.
            putBoolean("automatic_backup", destinations.isNotEmpty())
            remove("auto_backup")
            remove("auto_backup_path")
        }
    }

    private fun getStartDelay(): Int {
        val startDelayStr = preferences.getString("sleep_start_delta", "0") ?: "0"
        return startDelayStr.toIntOrNull() ?: 0
    }

    fun getCompactView(): Boolean {
        return preferences.getBoolean("compact_view", true)
    }

    fun getIgnoreEmptyDays(): Boolean {
        return preferences.getBoolean("ignore_empty_days", true)
    }

    fun getStatFunction(): StatFunction {
        if (preferences.getBoolean("use_median", false)) {
            return StatFunction.MEDIAN
        } else {
            return StatFunction.AVERAGE
        }
    }

    suspend fun storeSleep() {
        val sleep = Sleep()
        start?.let {
            sleep.start = it.time
        }
        stop?.let {
            sleep.stop = it.time
        }

        val startDelayMS = getStartDelay() * 60 * 1000
        if (sleep.start + startDelayMS > sleep.stop) {
            sleep.start = sleep.stop
        } else {
            sleep.start += startDelayMS
        }

        database.sleepDao().insert(sleep)

        // Drop start timestamp from preferences, it's in the database now.
        preferences.edit {
            remove("start")
        }
    }

    suspend fun insertSleep(sleep: Sleep) {
        database.sleepDao().insert(sleep)
    }

    private suspend fun insertSleeps(sleepList: List<Sleep>) {
        database.sleepDao().insert(sleepList)
    }

    suspend fun updateSleep(sleep: Sleep) {
        database.sleepDao().update(sleep)
    }

    suspend fun deleteSleep(sleep: Sleep) {
        database.sleepDao().delete(sleep)
    }

    suspend fun deleteAllSleep() {
        database.sleepDao().deleteAll()
    }

    suspend fun getSleepById(sid: Int): Sleep {
        return database.sleepDao().getById(sid)
    }

    fun getSleepsAfterLive(after: Date): LiveData<List<Sleep>> {
        // Callable, so we update if any of the below LiveDatas change.
        val afterSleep = {
            database.sleepDao().getAfterLive(after.time)
        }
        val useMedian = preferences.liveDataBoolean("use_median", false).switchMap { _ ->
            afterSleep()
        }
        return useMedian
    }

    suspend fun importData(context: Context, cr: ContentResolver, uri: Uri) {
        val inputStream = cr.openInputStream(uri)
        val ret = if (inputStream != null) {
            inputStream.use { importSleepsFromReader(InputStreamReader(it)) }
        } else {
            false
        }

        if (ret) {
            val text = context.getString(R.string.import_success)
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(context, text, duration)
            toast.show()
        }
    }

    /**
     * Parses CSV sleeps from [reader] and inserts the ones not already in the database. Returns
     * true on success. The [reader] is not closed; the caller owns it.
     */
    private suspend fun importSleepsFromReader(reader: Reader): Boolean {
        val importedSleeps = parseSleepsCsv(reader) ?: return false
        insertNewSleeps(importedSleeps)
        return true
    }

    /** Parses CSV sleeps from [reader], or returns null when the data is not a valid CSV. */
    private suspend fun parseSleepsCsv(reader: Reader): List<Sleep>? = withContext(
        Dispatchers.IO
    ) {
        try {
            val records: Iterable<CSVRecord> = CSVFormat.DEFAULT.parse(reader)
            val importedSleeps = mutableListOf<Sleep>()
            var first = true
            for (cells in records) {
                if (first) {
                    // Ignore the header.
                    first = false
                    continue
                }
                val sleep = Sleep()
                sleep.start = cells[1].toLong()
                sleep.stop = cells[2].toLong()
                if (cells.isSet(3)) {
                    sleep.rating = cells[3].toLong()
                }
                if (cells.isSet(4)) {
                    sleep.comment = cells[4]
                }
                if (cells.isSet(5)) {
                    sleep.wakes = cells[5].toIntOrNull() ?: 0
                }
                importedSleeps.add(sleep)
            }
            importedSleeps
        } catch (e: Exception) {
            Log.e(TAG, "parseSleepsCsv: parsing failed: $e")
            null
        }
    }

    /**
     * We have a speed vs memory usage trade-off here. Pay the cost of keeping all sleeps in
     * memory: the benefit is that inserting all of them once triggers a single notification of
     * observers. This means that importing 100s of sleeps is still ~instant, while it used to
     * take ~forever.
     */
    private suspend fun insertNewSleeps(importedSleeps: List<Sleep>) {
        val oldSleeps = database.sleepDao().getAll()
        val newSleeps = importedSleeps.subtract(oldSleeps.toSet())
        database.sleepDao().insert(newSleeps.toList())
    }

    /**
     * True when there is at least one sleep stored locally.
     */
    suspend fun hasSleeps(): Boolean {
        return database.sleepDao().count() > 0
    }

    /**
     * Restores sleeps from the Google Drive backup (gplay flavor only). Returns true on success.
     * With [override] the local sleeps are replaced by the backup; otherwise the backup is merged
     * into the existing data. The local data is only cleared after both the download and the
     * parse succeeded, so a failed fetch or a corrupt backup never wipes existing sleeps.
     */
    suspend fun restoreFromDrive(context: Context, email: String, override: Boolean): Boolean {
        val data = DriveBackend.download(context, email) ?: return false
        val sleeps = parseSleepsCsv(InputStreamReader(ByteArrayInputStream(data))) ?: return false
        if (override) {
            deleteAllSleep()
        }
        insertNewSleeps(sleeps)
        return true
    }

    suspend fun importDataFromCalendar(context: Context, calendarId: String) {
        // Query the calendar for events
        val importedSleeps = CalendarImport.queryForEvents(
            context, calendarId
        ).map(CalendarImport::mapEventToSleep)
        val oldSleeps = database.sleepDao().getAll()
        val newSleeps = importedSleeps.subtract(oldSleeps.toSet())

        // Insert the list of Sleep into DB
        insertSleeps(newSleeps.toList())

        // Show how many sleeps were imported.
        val text = context.resources.getQuantityString(
            R.plurals.imported_items,
            newSleeps.size,
            newSleeps.size
        )
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    suspend fun exportDataToCalendar(context: Context, calendarId: String) {
        val calendarSleeps = CalendarImport.queryForEvents(
            context, calendarId
        ).map(CalendarImport::mapEventToSleep)
        val sleeps = database.sleepDao().getAll()
        val exportedSleeps = sleeps.subtract(calendarSleeps.toSet())

        CalendarExport.exportSleep(context, calendarId, exportedSleeps.toList())

        // Show how many sleeps were exported.
        val text = context.resources.getQuantityString(
            R.plurals.exported_items,
            exportedSleeps.size,
            exportedSleeps.size
        )
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    /**
     * Backs up sleeps to all configured destinations. Drive accounts with "on_change" frequency
     * are uploaded via a WorkManager job, so the upload survives this call's coroutine being
     * cancelled (e.g. the app is closed) and retries on transient failure; "daily" accounts are
     * handled by their own periodic worker. Folder backups are written here directly.
     */
    suspend fun backupSleeps(context: Context, cr: ContentResolver) {
        if (!isAutomaticBackupEnabled()) return
        val destinations = getDestinations()
        if (destinations.isEmpty()) return
        // Enqueue the Drive uploads first: enqueueing is a fast, persisted WorkManager call that
        // is not lost if the app is closed mid-backup, unlike the folder write below.
        for (dest in destinations) {
            if (dest is BackupDestination.DriveAccount && dest.frequency != "daily") {
                DriveBackend.scheduleBackup(context, dest.email)
            }
        }
        for (dest in destinations) {
            if (dest is BackupDestination.LocalFolder) {
                backupSleepsToFolder(context, cr, dest.path)
            }
        }
    }

    /** The master switch gating all automatic backups; manual "Back up now" ignores it. */
    fun isAutomaticBackupEnabled(): Boolean = preferences.getBoolean("automatic_backup", false)

    /** True when a backup.csv exists in the folder destination [path]. */
    suspend fun hasFolderBackup(context: Context, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(path))
                    ?.findFile("backup.csv") != null
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Deletes the backup.csv written by [backupSleepsToFolder] from the folder destination
     * [path]. Returns true when the file is gone afterwards (deleted or never existed).
     */
    suspend fun deleteFolderBackup(context: Context, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(path))
                    ?: return@withContext false
                val file = folder.findFile("backup.csv") ?: return@withContext true
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "deleteFolderBackup: $e")
                false
            }
        }

    private suspend fun backupSleepsToFolder(context: Context, cr: ContentResolver, path: String) {
        if (path.isEmpty()) return
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return
        // Overwrite an existing backup rather than creating "backup (1).csv" etc.
        folder.findFile("backup.csv")?.delete()
        val backup = folder.createFile("text/csv", "backup.csv") ?: return
        exportDataToFile(context, cr, backup.uri, showToast = false)
    }

    /**
     * Serializes all sleeps to importable (non-pretty) CSV bytes, used by the Drive backup.
     */
    suspend fun serializeSleeps(): ByteArray = withContext(Dispatchers.IO) {
        val sleeps = database.sleepDao().getAll()
        val os = ByteArrayOutputStream()
        writeSleepsCsv(sleeps, os, prettyBackup = false)
        os.toByteArray()
    }

    /**
     * Uploads [data] to [email]'s Drive backup unless it is byte-identical to the last payload
     * this device successfully uploaded there, so automatic backups (daily worker, on-change)
     * don't re-send unchanged data. Returns true when the backup is up to date afterwards.
     * Manual "Back up now" bypasses this and always uploads, which also repairs a backup that
     * went missing remotely while the stored hash still matches.
     */
    suspend fun uploadToDriveIfChanged(context: Context, email: String, data: ByteArray): Boolean {
        val hash = sha256(data)
        if (hash == getDriveBackupHash(email)) {
            return true
        }
        val success = DriveBackend.upload(context, email, data)
        if (success) {
            setDriveBackupHash(email, hash)
        }
        return success
    }

    private fun getDriveBackupHash(email: String): String? =
        preferences.getString("drive_backup_hash_$email", null)

    /** Records the hash of [email]'s last uploaded payload; null forgets it (backup deleted). */
    fun setDriveBackupHash(email: String, hash: String?) {
        preferences.edit {
            if (hash == null) {
                remove("drive_backup_hash_$email")
            } else {
                putString("drive_backup_hash_$email", hash)
            }
        }
    }

    fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    suspend fun exportDataToFile(
        context: Context,
        cr: ContentResolver,
        uri: Uri,
        showToast: Boolean
    ) {
        val prettyBackup = preferences.getBoolean("pretty_backup", false)
        val sleeps = database.sleepDao().getAll()

        try {
            cr.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "exportData: takePersistableUriPermission() failed for write")
        }

        var os: OutputStream? = null
        try {
            os = cr.openOutputStream(uri)
            if (os == null) {
                Log.e(TAG, "exportData: openOutputStream() failed")
                return
            }
            writeSleepsCsv(sleeps, os, prettyBackup)
        } catch (e: Exception) {
            if (showToast) {
                val text = String.format(context.getString(R.string.export_failure), e)
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(context, text, duration)
                toast.show()
            } else {
                Log.e(TAG, "exportDataToFile, failed: $e")
            }
            return
        } finally {
            try {
                os?.close()
            } catch (_: Exception) {
            }
        }

        if (!showToast) {
            return
        }
        val text = context.getString(R.string.export_success)
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    /**
     * Writes [sleeps] as CSV into [os]. Shared by the file export and the Drive backup. The stream
     * is flushed but not closed; the caller owns it.
     */
    fun writeSleepsCsv(sleeps: List<Sleep>, os: OutputStream, prettyBackup: Boolean) {
        val writer = CSVPrinter(OutputStreamWriter(os, "UTF-8"), CSVFormat.DEFAULT)
        if (prettyBackup) {
            writer.printRecord("sid", "start", "stop", "length", "rating", "comment", "wakes")
        } else {
            writer.printRecord("sid", "start", "stop", "rating", "comment", "wakes")
        }
        for (sleep in sleeps) {
            if (prettyBackup) {
                val durationMS = sleep.stop - sleep.start

                writer.printRecord(
                    sleep.sid,
                    formatTimestamp(Date(sleep.start), getCompactView()),
                    formatTimestamp(Date(sleep.stop), getCompactView()),
                    formatDuration(durationMS / 1000, getCompactView()),
                    sleep.rating,
                    sleep.comment,
                    sleep.wakes
                )
            } else {
                writer.printRecord(
                    sleep.sid,
                    sleep.start,
                    sleep.stop,
                    sleep.rating,
                    sleep.comment,
                    sleep.wakes
                )
            }
        }
        writer.flush()
    }

    private const val TAG = "DataModel"

    fun getSleepCountStat(sleeps: List<Sleep>): String {
        return sleeps.size.toString()
    }

    /**
     * Calculates the avg of sleeps.
     */
    fun getSleepDurationStat(sleeps: List<Sleep>, compactView: Boolean): String {
        var sum: Long = 0
        for (sleep in sleeps) {
            var diff = sleep.stop - sleep.start
            diff /= 1000
            sum += diff
        }
        val count = sleeps.size
        return if (count == 0) {
            ""
        } else formatDuration(sum / count, compactView)
    }

    enum class StatFunction {
        AVERAGE, MEDIAN
    }

    /**
     * Sums up sleeps per day, and then calculate the avg of those sums.
     */
    fun getSleepDurationDailyStat(
        sleeps: List<Sleep>,
        compactView: Boolean,
        ignoreEmptyDays: Boolean,
        statFunction: StatFunction
    ): String {
        // Day -> sum (in seconds) map.
        val sums = HashMap<Long, Long>()
        var minKey: Long = Long.MAX_VALUE
        var maxKey: Long = 0
        for (sleep in sleeps) {
            var diff = sleep.stop - sleep.start
            diff /= 1000

            // Calculate stop day
            val stopDate = Calendar.getInstance()
            stopDate.timeInMillis = sleep.stop

            val day = Calendar.getInstance()
            day.timeInMillis = 0
            val startYear = stopDate.get(Calendar.YEAR)
            day.set(Calendar.YEAR, startYear)
            val startMonth = stopDate.get(Calendar.MONTH)
            day.set(Calendar.MONTH, startMonth)
            val startDay = stopDate.get(Calendar.DAY_OF_MONTH)
            day.set(Calendar.DAY_OF_MONTH, startDay)
            val key = day.timeInMillis
            minKey = minOf(minKey, key)
            maxKey = maxOf(maxKey, key)

            val sum = sums[key]
            if (sum != null) {
                sums[key] = sum + diff
            } else {
                sums[key] = diff
            }
        }

        if (sums.size == 0) {
            return formatDuration(0, compactView)
        }

        // Now determine the number of covered days. This is usually just the number of keys, but it
        // can be more, in case a whole 24h period was left out.
        val msPerDay = 86400 * 1000
        var count = (maxKey - minKey) / msPerDay + 1
        if (ignoreEmptyDays) {
            count = sums.keys.size.toLong()
        }
        val duration: Long = when (statFunction) {
            StatFunction.AVERAGE -> sums.values.sum() / count
            StatFunction.MEDIAN -> median(sums.values.toLongArray()).roundToLong()
        }

        return formatDuration(duration, compactView)
    }

    fun formatDuration(seconds: Long, compactView: Boolean): String {
        if (compactView) {
            return String.format(
                Locale.getDefault(), "%d:%02d",
                seconds / 3600, seconds % 3600 / 60
            )
        }

        return String.format(
            Locale.getDefault(), "%d:%02d:%02d",
            seconds / 3600, seconds % 3600 / 60,
            seconds % 60
        )
    }

    fun formatTimestamp(date: Date, compactView: Boolean): String {
        val sdf = if (compactView) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX", Locale.getDefault())
        }
        return sdf.format(date)
    }

    fun formatDateTime(date: Date, asTime: Boolean, compactView: Boolean): String {
        val sdf = if (asTime) {
            if (compactView) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            }
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        return sdf.format(date)
    }

    /**
     * Returns the subset of [sleeps] which stop after [after].
     */
    fun filterSleeps(sleeps: List<Sleep>, after: Date): List<Sleep> {
        return sleeps.filter { it.stop > after.time }
    }

    private fun getPreferencesToken(
        preferences: SharedPreferences,
        name: String,
        index: Int,
        default: Int
    ): Int {
        val pref = preferences.getString(name, "")
        if (pref == null) {
            return default
        }

        val tokens = pref.split(":").toTypedArray()
        if (tokens.size != 2) {
            return default
        }

        return tokens[index].toInt()
    }

    fun getBedtimeHour(preferences: SharedPreferences): Int {
        return getPreferencesToken(preferences, "bedtime", 0, 22)
    }

    fun getBedtimeMinute(preferences: SharedPreferences): Int {
        return getPreferencesToken(preferences, "bedtime", 1, 0)
    }

    fun getWakeupHour(preferences: SharedPreferences): Int {
        return getPreferencesToken(preferences, "wakeup", 0, 7)
    }

    fun getWakeupMinute(preferences: SharedPreferences): Int {
        return getPreferencesToken(preferences, "wakeup", 1, 0)
    }

    fun handleWindowInsets(activity: AppCompatActivity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Handle edge-to-edge mode
            val rootView = activity.findViewById<View>(R.id.root)
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
                WindowInsetsCompat.CONSUMED
            }
            val resources = activity.resources
            val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMask == Configuration.UI_MODE_NIGHT_NO) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = true
            }
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
