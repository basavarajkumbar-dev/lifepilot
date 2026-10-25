package com.lifepilot.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifepilot.app.ui.navigation.LifePilotRoot
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Route requested by a notification tap ("Open"), consumed by the navigation host. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as LifePilotApp).container
        setContent {
            LifePilotRoot(container = container, pendingRoute = pendingRoute, onRouteConsumed = { pendingRoute.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        routeFor(intent)?.let { pendingRoute.value = it }
    }

    companion object {
        /** Maps lifepilot://occurrence/{id} and lifepilot://habit/{id} to navigation routes. */
        fun routeFor(intent: Intent?): String? {
            val uri = intent?.data ?: return null
            if (uri.scheme != "lifepilot") return null
            val id = uri.lastPathSegment?.toLongOrNull() ?: return null
            return when (uri.host) {
                "occurrence" -> "occurrence/$id"
                "habit" -> "habit/$id"
                else -> null
            }
        }
    }
}
