/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.ContentResolver
import android.content.Context
import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

/**
 * This callback handles deletion of one recorded sleep in the sleep list. Listens to left/right swipes only.
 */
class SleepTouchCallback(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val viewModel: MainViewModel,
    private val adapter: SleepsAdapter
) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {
        val sleep = adapter.data[viewHolder.bindingAdapterPosition]
        viewModel.deleteSleep(sleep, context, contentResolver)

        val view = viewHolder.itemView
        val snackbar = Snackbar.make(view, R.string.deleted, Snackbar.LENGTH_LONG)
        snackbar.setAction(context.getString(R.string.undo)) {
            viewModel.insertSleep(sleep)
        }

        snackbar.show()
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is SleepsAdapter.SleepViewHolder) {
            viewHolder.showSwipeDelete(dX > 0)
            getDefaultUIUtil().onDraw(
                c, recyclerView, viewHolder.swipeable, dX, dY, actionState, isCurrentlyActive
            )
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder is SleepsAdapter.SleepViewHolder) {
            getDefaultUIUtil().clearView(viewHolder.swipeable)
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
