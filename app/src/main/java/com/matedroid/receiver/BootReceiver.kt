package com.matedroid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.matedroid.data.sync.TpmsPressureWorker

/**
 * BroadcastReceiver that reschedules periodic workers after device reboot.
 * Ensures TPMS pressure monitoring continues after the device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, rescheduling workers")

            // Reschedule TPMS pressure monitoring
            TpmsPressureWorker.schedulePeriodicWork(context)
        }
    }
}
