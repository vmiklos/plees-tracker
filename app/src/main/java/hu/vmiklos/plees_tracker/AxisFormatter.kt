/*
 * Copyright 2021 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker

import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** [ValueFormatter] for showing dates on the axis of a graph. */
class DateAxisFormatter : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(value.toLong()))
    }
}

/** [ValueFormatter] for showing times on the axis of a graph, converted to UTC. */
class TimeAxisFormatter : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(value.toLong()))
    }
}

/** [ValueFormatter] for showing float numbers on the axis of a graph, up to one decimal place. */
class FloatAxisFormatter : ValueFormatter() {
    private val decimalFormat = DecimalFormat("0.#")
        .apply { isDecimalSeparatorAlwaysShown = false }

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return decimalFormat.format(value)
    }
}
