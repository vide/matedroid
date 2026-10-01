package com.matedroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.matedroid.data.sync.ChargingNotificationWorker
import com.matedroid.data.sync.DataSyncWorker
import com.matedroid.ui.navigation.NavGraph
import com.matedroid.ui.theme.MateDroidTheme
import com.matedroid.widget.CarWidgetUpdateWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntent = intent
        if (intent.hasExtra("EXTRA_CAR_ID")) {
            CarWidgetUpdateWorker.scheduleImmediateUpdate(this)
        }
        if (savedInstanceState == null) {
            // A real app open, not a configuration change: refresh the local data and clear
            // any charging notification a dead process may have left behind. These used to
            // run from Application.onCreate, i.e. on every background process start too.
            DataSyncWorker.enqueueOnAppOpen(this)
            ChargingNotificationWorker.runNow(this)
        }
        enableEdgeToEdge()
        setContent {
            MateDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(intent = currentIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent = intent
    }
}
