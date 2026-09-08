package com.netmic.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.netmic.app.service.AudioStreamService

/**
 * Riavvia il ForegroundService AudioStreamService dopo l'avvio del dispositivo,
 * garantendo il funzionamento continuo 24 ore su 24 anche dopo riavvii o interruzioni di corrente.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, AudioStreamService::class.java).apply {
                action = AudioStreamService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}