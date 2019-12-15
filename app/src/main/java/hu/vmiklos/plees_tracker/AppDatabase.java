package hu.vmiklos.plees_tracker;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Contains the database holder and serves as the main access point for the
 * stored data.
 */
@Database(entities = {Sleep.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase
{
    public abstract SleepDao sleepDao();
}