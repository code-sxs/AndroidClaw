// MainActivity.kt
// 应用主 Activity

package com.androidclaw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.androidclaw.app.ui.navigation.AndroidClawNavHost
import com.androidclaw.app.ui.theme.AndroidClawTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AndroidClawTheme {
                // 遵循 Material Design 3
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 导航宿主
                    AndroidClawNavHost()
                }
            }
        }
    }
}
