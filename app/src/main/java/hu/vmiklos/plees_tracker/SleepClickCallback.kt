/*
 * Copyright 2023 Miklos Vajna
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * This callback handles editing of one recorded sleep in the sleep list. Listens to clicks only.
 */
class SleepClickCallback(
    private val mainActivity: MainActivity,
    private val adapter: SleepsAdapter,
    private val recyclerView: RecyclerView
) : View.OnClickListener {
    override fun onClick(view: View?) {
        if (view == null) {
            return
        }
        val itemPosition = recyclerView.getChildLayoutPosition(view)
        val sleep = adapter.data[itemPosition]

        val intent = Intent(mainActivity, SleepActivity::class.java)
        val bundle = Bundle()
        bundle.putInt("sid", sleep.sid)
        intent.putExtras(bundle)
        mainActivity.startActivity(intent)
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
