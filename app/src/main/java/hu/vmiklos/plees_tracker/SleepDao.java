package hu.vmiklos.plees_tracker;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Accesses the database of Sleep objects.
 */
@Dao
public interface SleepDao {
    @Query("SELECT * FROM sleep") List<Sleep> getAll();

    @Insert void insert(Sleep sleep);
}
