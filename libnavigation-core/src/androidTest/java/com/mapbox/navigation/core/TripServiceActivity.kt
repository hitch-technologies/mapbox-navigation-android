package com.mapbox.navigation.core

import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.annotation.module.MapboxModuleType
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message
import com.mapbox.base.common.logger.model.Tag
import com.mapbox.common.module.provider.MapboxModuleProvider
import com.mapbox.navigation.base.TimeFormat.TWENTY_FOUR_HOURS
import com.mapbox.navigation.base.internal.VoiceUnit.METRIC
import com.mapbox.navigation.base.internal.extensions.inferDeviceLocale
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.trip.notification.TripNotification
import com.mapbox.navigation.core.internal.MapboxDistanceFormatter
import com.mapbox.navigation.core.test.R
import com.mapbox.navigation.core.trip.service.MapboxTripService
import com.mapbox.navigation.utils.internal.ThreadController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class TripServiceActivity : AppCompatActivity() {

    private var mainJobController = ThreadController.getMainScopeAndRootJob()
    private lateinit var tripNotification: TripNotification
    private lateinit var mapboxTripService: MapboxTripService
    private var textUpdateJob: Job = Job()
    private val dummyLogger = object : Logger {
        override fun d(tag: Tag?, msg: Message, tr: Throwable?) {}

        override fun e(tag: Tag?, msg: Message, tr: Throwable?) {}

        override fun i(tag: Tag?, msg: Message, tr: Throwable?) {}

        override fun v(tag: Tag?, msg: Message, tr: Throwable?) {}

        override fun w(tag: Tag?, msg: Message, tr: Throwable?) {}
    }
    private lateinit var toggleNotification: Button
    private lateinit var notifyTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_service)

        toggleNotification = findViewById(R.id.toggleNotification)
        notifyTextView = findViewById(R.id.notifyTextView)

        tripNotification = MapboxModuleProvider.createModule(
            MapboxModuleType.NavigationTripNotification,
            ::paramsProvider
        )

        mapboxTripService =
            MapboxTripService(applicationContext, tripNotification, dummyLogger)

        toggleNotification.setOnClickListener {
            when (mapboxTripService.hasServiceStarted()) {
                true -> {
                    stopService()
                }
                false -> {
                    mapboxTripService.startService()
                    changeText()
                    toggleNotification.text = "Stop"
                }
            }
        }
    }

    private fun paramsProvider(type: MapboxModuleType): Array<Pair<Class<*>?, Any?>> {
        val formatter = MapboxDistanceFormatter.builder()
            .withRoundingIncrement(Rounding.INCREMENT_FIFTY)
            .withUnitType(METRIC)
            .withLocale(this.inferDeviceLocale())
            .build(this)

        val options = NavigationOptions.Builder(applicationContext)
            .distanceFormatter(formatter)
            .timeFormatType(TWENTY_FOUR_HOURS)
            .build()

        return arrayOf(NavigationOptions::class.java to options)
    }

    private fun stopService() {
        textUpdateJob.cancel()
        mapboxTripService.stopService()
        toggleNotification.text = "Start"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
        ThreadController.cancelAllNonUICoroutines()
        ThreadController.cancelAllUICoroutines()
    }

    private fun changeText() {
        textUpdateJob = mainJobController.scope.launch {
            while (isActive) {
                val text = "Time elapsed: + ${SystemClock.elapsedRealtime()}"
                notifyTextView.text = text
                mapboxTripService.updateNotification(null)
                delay(1000L)
            }
        }
    }
}
