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
import java.util.List;

/**
 * This is the adapter between RecyclerView and SleepDao.
 */
public class SleepsAdapter
    extends RecyclerView.Adapter<SleepsAdapter.SleepViewHolder>
{
    private List<Sleep> mData;
    private Context mContext;
    private LayoutInflater mLayoutInflater;

    public SleepsAdapter(Context context)
    {
        mData = new ArrayList<>();
        mContext = context;
        mLayoutInflater = (LayoutInflater)mContext.getSystemService(
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
        holder.bind(mData.get(position));
    }

    @Override public int getItemCount() { return mData.size(); }

    public void setData(List<Sleep> data)
    {
        if (mData == null)
        {
            mData = data;
            return;
        }

        SleepDiffCallback sleepDiffCallback =
            new SleepDiffCallback(mData, data);
        DiffUtil.DiffResult diffResult =
            DiffUtil.calculateDiff(sleepDiffCallback);
        mData.clear();
        mData.addAll(data);
        diffResult.dispatchUpdatesTo(this);
    }

    class SleepViewHolder extends RecyclerView.ViewHolder
    {
        private View mView;

        public SleepViewHolder(View view)
        {
            super(view);
            mView = view;
        }

        void bind(final Sleep sleep)
        {
            if (sleep == null)
            {
                return;
            }

            TextView id = mView.findViewById(R.id.sleep_id);
            DataModel dataModel = DataModel.getDataModel();
            long durationMS = sleep.stop - sleep.start;
            String duration = DataModel.formatDuration(durationMS / 1000);
            id.setText(duration);
        }
    }

    class SleepDiffCallback extends DiffUtil.Callback
    {
        private final List<Sleep> mOldList;
        private final List<Sleep> mNewList;

        public SleepDiffCallback(List<Sleep> oldList, List<Sleep> newList)
        {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override public int getOldListSize() { return mOldList.size(); }

        @Override public int getNewListSize() { return mNewList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition)
        {
            return mOldList.get(oldItemPosition).sid ==
                mNewList.get(newItemPosition).sid;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition,
                                          int newItemPosition)
        {
            return mOldList.get(oldItemPosition)
                .equals(mNewList.get(newItemPosition));
        }
    }
}
