/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList
import java.util.Date

/**
 * This is the adapter between RecyclerView and SleepDao.
 */
class SleepsAdapter(
    private val viewModel: MainViewModel
) : RecyclerView.Adapter<SleepsAdapter.SleepViewHolder>() {
    var data: List<Sleep> = ArrayList()
        set(newData) {
            val previousData = field
            field = newData
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int {
                    return previousData.size
                }

                override fun getNewListSize(): Int {
                    return newData.size
                }

                /**
                 * Compares old and new based on their ID only.
                 */
                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val oldSid = previousData[oldItemPosition].sid
                    val newSid = newData[newItemPosition].sid
                    return oldSid == newSid
                }

                /**
                 * Compares old and new based on their value.
                 */
                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    return previousData[oldItemPosition] == newData[newItemPosition]
                }
            }).dispatchUpdatesTo(this)
        }

    var clickCallback: View.OnClickListener? = null

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SleepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_sleep_item, parent, false)
        clickCallback?.let {
            view.setOnClickListener(it)
        }
        return SleepViewHolder(view)
    }

    override fun onBindViewHolder(holder: SleepViewHolder, position: Int) {
        val sleep = data[position]
        holder.start.text = DataModel.formatTimestamp(Date(sleep.start))
        holder.stop.text = DataModel.formatTimestamp(Date(sleep.stop))
        val durationMS = sleep.stop - sleep.start
        val durationText = DataModel.formatDuration(durationMS / 1000)
        holder.duration.text = durationText
        holder.rating.rating = sleep.rating.toFloat()
        holder.rating.onRatingBarChangeListener = SleepRateCallback(viewModel, sleep)
    }

    /**
     * The view holder holds all views that will display one Sleep.
     */
    inner class SleepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val start: TextView = view.findViewById(R.id.sleep_item_start)
        val stop: TextView = view.findViewById(R.id.sleep_item_stop)
        val duration: TextView = view.findViewById(R.id.sleep_item_duration)
        val rating: RatingBar = view.findViewById(R.id.sleep_item_rating)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
