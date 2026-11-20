package com.feedcap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.feedcap.app.data.Store
import com.feedcap.app.ui.HomeScreen
import com.feedcap.app.ui.design.FcTheme
import com.feedcap.app.ui.onboarding.OnboardingFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store.get(this)
        setContent {
            val theme by store.theme.collectAsState()
            val accent by store.accent.collectAsState()
            FcTheme(theme, accent) {
                val onboarded by store.onboarded.collectAsState()
                Crossfade(onboarded, animationSpec = tween(400), label = "root") { done ->
                    if (done) HomeScreen(store) else OnboardingFlow(store)
                }
            }
        }
    }
}
