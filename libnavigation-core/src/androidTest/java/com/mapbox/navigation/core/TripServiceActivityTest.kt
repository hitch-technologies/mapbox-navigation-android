package com.mapbox.navigation.core

import androidx.test.espresso.Espresso.onIdle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.mapbox.navigation.core.test.R
import com.mapbox.navigation.testing.ui.NotificationTestRule
import com.schibsted.spain.barista.assertion.BaristaEnabledAssertions.assertEnabled
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertContains
import com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.assertDisplayed
import com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class TripServiceActivityTest :
    NotificationTestRule<TripServiceActivity>(TripServiceActivity::class.java) {

    @Before
    fun setup() {
        onIdle()
    }

    @Test
    fun checkStartButtonAccessible() {
        R.id.toggleNotification.let {
            assertDisplayed(it)
            assertEnabled(it)
            assertContains(it, "Start")
        }
    }

    @Test
    fun checkNotificationViewContent() {
        R.id.notifyTextView.let {
            assertContains(it, "")
        }

        R.id.toggleNotification.let {
            clickOn(it)
        }

        R.id.notifyTextView.let {
            assertDisplayed(it)
            assertContains(it, "Time elapsed: + ")
        }
    }

    @Test
    fun checkNotificationContent() {
        R.id.toggleNotification.let {
            clickOn(it)
        }

        R.id.notifyTextView.let {
            assertDisplayed(it)
            assertContains(it, "Time elapsed: + ")
        }

        uiDevice.run {
            openNotification()
            val etaContent = By.res("com.mapbox.navigation.core.test:id/etaContent")
            val freeDriveText = By.res("com.mapbox.navigation.core.test:id/freeDriveText")
            wait(Until.hasObject(etaContent), 1000)
            wait(Until.hasObject(freeDriveText), 1000)

            assertFalse(hasObject(etaContent))
            assertTrue(hasObject(freeDriveText))
            pressBack()
        }
    }
}
