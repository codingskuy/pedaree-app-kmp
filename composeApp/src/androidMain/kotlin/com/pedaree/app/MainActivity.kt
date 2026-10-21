package com.pedaree.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // testTag terekspos sebagai resource-id agar terbaca selector Maestro (`id:`).
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                PedareeApp(depsPengembangan())
            }
        }
    }
}
