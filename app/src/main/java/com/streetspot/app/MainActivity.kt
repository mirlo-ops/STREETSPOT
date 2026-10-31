package com.streetspot.app

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import kotlinx.coroutines.flow.MutableSharedFlow
import com.streetspot.app.ui.StreetSpotRoot

class MainActivity : ComponentActivity() {
    private val widgetNavigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            StreetSpotRoot(
                initialRoute = routeFrom(intent),
                externalNavigationRequests = widgetNavigationRequests,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let(widgetNavigationRequests::tryEmit)
    }

    private fun routeFrom(intent: Intent?): String? = when (
        intent?.getStringExtra(EXTRA_INITIAL_ROUTE)
    ) {
        ROUTE_HOME -> ROUTE_HOME
        ROUTE_SPOTDEX -> ROUTE_SPOTDEX
        else -> null
    }

    companion object {
        const val EXTRA_INITIAL_ROUTE = "com.streetspot.app.extra.INITIAL_ROUTE"
        const val ROUTE_HOME = "home"
        const val ROUTE_SPOTDEX = "spotdex"
    }
}
