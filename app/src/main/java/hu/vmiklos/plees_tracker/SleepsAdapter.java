/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * This is the adapter between RecyclerView and SleepDao.
 */
public class SleepsAdapter
    extends RecyclerView.Adapter<SleepsAdapter.SleepViewHolder>
{
    private List<Sleep> mData;
    private LayoutInflater mLayoutInflater;

    public SleepsAdapter(Context context)
    {
        mData = new ArrayList<>();
        mLayoutInflater = (LayoutInflater)context.getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public SleepViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                              int viewType)
    {
        View view =
            mLayoutInflater.inflate(R.layout.layout_sleep_item, parent, false);
        return new SleepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SleepViewHolder holder, int position)
    {
        Sleep sleep = mData.get(position);
        holder.start.setText(DataModel.formatTimestamp(new Date(sleep.start)));
        holder.stop.setText(DataModel.formatTimestamp(new Date(sleep.stop)));
        long durationMS = sleep.stop - sleep.start;
        String durationText = DataModel.formatDuration(durationMS / 1000);
        holder.duration.setText(durationText);
    }

    @Override public int getItemCount() { return mData.size(); }

    public void setData(final List<Sleep> newData)
    {
        final List<Sleep> previousData = mData;
        mData = newData;
        DiffUtil
            .calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize()
                {
                    return previousData != null ? previousData.size() : 0;
                }

                @Override public int getNewListSize()
                {
                    return newData != null ? newData.size() : 0;
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition,
                                               int newItemPosition)
                {
                    return previousData.get(oldItemPosition).sid ==
                        newData.get(newItemPosition).sid;
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition,
                                                  int newItemPosition)
                {
                    // No need to do deep comparison of data since the
                    // start/stop of a sleep never changes.
                    return true;
                }
            })
            .dispatchUpdatesTo(this);
    }

    /**
     * The view holder holds all views that will display one Sleep.
     */
    class SleepViewHolder extends RecyclerView.ViewHolder
    {
        public final TextView start;
        public final TextView stop;
        public final TextView duration;

        public SleepViewHolder(View view)
        {
            super(view);
            start = view.findViewById(R.id.sleep_item_start);
            stop = view.findViewById(R.id.sleep_item_stop);
            duration = view.findViewById(R.id.sleep_item_duration);
        }
    }
}
