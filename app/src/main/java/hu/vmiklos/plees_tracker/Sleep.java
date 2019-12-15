package hu.vmiklos.plees_tracker;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Represents one tracked sleep.
 */
@Entity public class Sleep
{
    @PrimaryKey(autoGenerate = true) public int sid;

    @ColumnInfo(name = "start_date") public long start;

    @ColumnInfo(name = "stop_date") public long stop;
}