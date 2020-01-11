/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import java.util.Date

/**
 * This is the adapter between RecyclerView and SleepDao.
 */
class SleepsAdapter : RecyclerView.Adapter<SleepsAdapter.SleepViewHolder>() {
    private var mData: List<Sleep>? = null

    override fun getItemCount(): Int {
        return mData!!.size
    }

    var data: List<Sleep>?
        get() = mData
        set(newData) {
            val previousData = mData
            mData = newData
            DiffUtil
                    .calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize(): Int {
                            return previousData?.size ?: 0
                        }

                        override fun getNewListSize(): Int {
                            return newData?.size ?: 0
                        }

                        override fun areItemsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean {
                            val oldSid = previousData!![oldItemPosition].sid
                            val newSid = newData!![newItemPosition].sid
                            return oldSid == newSid
                        }

                        override fun areContentsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean {
                            // No need to do deep comparison of data since the
                            // start/stop of a sleep never changes.
                            return true
                        }
                    })
                    .dispatchUpdatesTo(this)
        }

    init {
        mData = ArrayList()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SleepViewHolder {
        val view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_sleep_item, parent, false)
        return SleepViewHolder(view)
    }

    override fun onBindViewHolder(holder: SleepViewHolder, position: Int) {
        val sleep = mData!![position]
        holder.start!!.setText(
                DataModel.formatTimestamp(Date(sleep.start)))
        holder.stop!!.setText(
                DataModel.formatTimestamp(Date(sleep.stop)))
        val durationMS = sleep.stop - sleep.start
        val durationText = DataModel.formatDuration(durationMS / 1000)
        holder.duration!!.setText(durationText)
    }

    /**
     * The view holder holds all views that will display one Sleep.
     */
    public inner class SleepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var start: TextView? = null
        var stop: TextView? = null
        var duration: TextView? = null

        init {
            start = view.findViewById(R.id.sleep_item_start)
            stop = view.findViewById(R.id.sleep_item_stop)
            duration = view.findViewById(R.id.sleep_item_duration)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
