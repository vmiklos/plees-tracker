/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import android.app.Application;
import android.provider.ContactsContract;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * View model for the sleeps, which survives activity restarts like device
 * rotation.
 */
public class SleepViewModel extends AndroidViewModel
{
    public SleepViewModel(Application application) { super(application); }

    LiveData<List<Sleep>> getSleeps()
    {
        DataModel dataModel = DataModel.getDataModel();
        return dataModel.getSleepsLive();
    }
}
