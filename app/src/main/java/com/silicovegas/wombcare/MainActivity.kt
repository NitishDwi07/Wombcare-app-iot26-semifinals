package com.silicovegas.wombcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.silicovegas.wombcare.core.navigation.WombCareNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All routing lives in [WombCareNavHost], which reacts to Firebase
 * auth state — so this class stays a thin shell.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WombCareNavHost()
        }
    }
}
