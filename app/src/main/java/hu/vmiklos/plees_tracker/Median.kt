/*
 * Copyright (c) 2020 The Algorithms
 *
 * SPDX-License-Identifier: MIT
 */

package hu.vmiklos.plees_tracker

import java.util.Arrays.sort

/**
 * Calculates the median of an array of Int
 *
 * @param values is an array of Int
 * @return the middle number of the array
 */
fun median(values: LongArray): Double {
    sort(values)
    return when {
        values.size % 2 == 0 -> getHalfwayBetweenMiddleValues(values)
        else -> getMiddleValue(values)
    }
}

/**
 * Calculates the middle number of an array when the size is an even number
 *
 * @param values is an array of Int
 * @return the middle number of the array
 */
private fun getHalfwayBetweenMiddleValues(values: LongArray): Double {
    val arraySize = values.size
    val sumOfMiddleValues = (values[arraySize / 2] + values[(arraySize / 2) - 1 ])
    return sumOfMiddleValues / 2.0
}

/**
 * Calculates the middle number of an array when the size is an odd number
 *
 * @param values is an array of Int
 * @return the middle number of the array
 */
private fun getMiddleValue(values: LongArray): Double {
    return values[values.size / 2].toDouble()
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
