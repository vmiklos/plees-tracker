package hu.vmiklos.plees_tracker;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Sleep.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase
{
    public abstract SleepDao sleepDao();
}