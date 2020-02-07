/*
 * Copyright 2020 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
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
        val itemPosition = this.recyclerView.getChildLayoutPosition(view)
        val sleep = this.adapter.data[itemPosition]

        val intent = Intent(mainActivity, SleepActivity::class.java)
        val bundle = Bundle()
        bundle.putInt("sid", sleep.sid)
        intent.putExtras(bundle)
        mainActivity.startActivity(intent)
    }

    companion object {
        private val TAG = "SleepClickCallback"
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
