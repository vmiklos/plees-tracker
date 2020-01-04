/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

/**
 * This callback handles deletion of one recorded sleep in the sleep list.
 */
public class SleepTouchCallback extends ItemTouchHelper.SimpleCallback
{
    private static final String TAG = "SleepTouchCallback";
    private SleepsAdapter mAdapter;

    public SleepTouchCallback(SleepsAdapter adapter)
    {
        // Listen to left/right swipes only.
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        mAdapter = adapter;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target)
    {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                         int direction)
    {
        final DataModel dataModel = DataModel.getDataModel();
        final Sleep sleep =
            mAdapter.getData().get(viewHolder.getAdapterPosition());
        dataModel.deleteSleep(sleep);

        View view = viewHolder.itemView;
        Snackbar snackbar =
            Snackbar.make(view, R.string.deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction(dataModel.getString(R.string.undo),
                           new View.OnClickListener() {
                               @Override public void onClick(View v)
                               {
                                   dataModel.insertSleep(sleep);
                               }
                           });

        snackbar.show();
    }
}
