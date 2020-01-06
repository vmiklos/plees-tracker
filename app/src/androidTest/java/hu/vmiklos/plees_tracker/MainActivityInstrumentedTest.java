/*
 * Copyright 2019 Miklos Vajna. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package hu.vmiklos.plees_tracker;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented tests for MainActivity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityInstrumentedTest
{
    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
        new ActivityScenarioRule<>(MainActivity.class);

    @Test public void testCountStat()
    {
        DataModel dataModel = DataModel.Companion.getDataModel();
        dataModel.deleteSleeps();

        onView(withId(R.id.start_stop)).perform(click());
        onView(withId(R.id.start_stop)).perform(click());
        onView(withId(R.id.count_stat)).check(matches(withText("1")));
    }
}
