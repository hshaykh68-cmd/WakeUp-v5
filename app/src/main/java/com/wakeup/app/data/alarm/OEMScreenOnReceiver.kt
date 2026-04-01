package com.wakeup.app.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.wakeup.app.domain.model.Alarm
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Nuclear option receiver for extremely aggressive OEMs (primarily Xiaomi).
 * 
 * This receiver listens for SCREEN_ON broadcasts which are triggered when the user
 * turns on their screen. On Xiaomi devices with aggressive battery optimization,
 * this can be the only reliable way to wake the app to check if an alarm should fire.
 * 
 * This is used as a last resort backup when:
 * 1. The alarm is scheduled within the next few minutes
 * 2. The device is from an extremely aggressive OEM
 * 3. Other scheduling methods may have been killed
 */
@AndroidEntryPoint
class OEMScreenOnReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmSchedulerImpl
    
    companion object {
        private val registeredAlarms = mutableSetOf<String>()
        private var isReceiverRegistered = false
        
        /**
         * Register this receiver for a specific alarm.
         * Should be called when scheduling alarms for aggressive OEMs.
         */
        fun registerAlarm(context: Context, alarm: Alarm) {
            registeredAlarms.add(alarm.id)
            
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
                
                val receiver = OEMScreenOnReceiver()
                context.applicationContext.registerReceiver(receiver, filter)
                isReceiverRegistered = true
            }
        }
        
        /**
         * Unregister this receiver for a specific alarm.
         */
        fun unregisterAlarm(context: Context, alarm: Alarm) {
            registeredAlarms.remove(alarm.id)
            
            // If no more alarms are registered, we could unregister the receiver
            // but we keep it active for simplicity
        }
        
        /**
         * Clear all registered alarms.
         */
        fun clearAll() {
            registeredAlarms.clear()
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                // Check if any registered alarm should fire soon
                checkAlarmsOnWake(context)
            }
        }
    }
    
    private fun checkAlarmsOnWake(context: Context) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        
        // This would need to be connected to the alarm repository
        // For now, this is a placeholder implementation
        // In production, you'd query the database for upcoming alarms
        
        // If there's an alarm within the next 2 minutes that we haven't
        // triggered yet, we should trigger it now
        
        // Note: This is a safety net - the primary alarm scheduling
        // should handle normal cases. This is only for when the
        // OEM has killed our scheduled alarms.
    }
}

/**
 * Time tick receiver that checks for missed alarms every minute.
 * This is a less aggressive backup that works on all devices.
 */
@AndroidEntryPoint
class OEMTimeTickReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmSchedulerImpl
    
    companion object {
        private var lastCheckTime: Long = 0
        
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK) // Every minute
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            
            val receiver = OEMTimeTickReceiver()
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        
        // Prevent duplicate checks within the same minute
        if (now - lastCheckTime < 55000) { // 55 seconds
            return
        }
        lastCheckTime = now
        
        // Check for any missed alarms that should have fired
        // This would query the alarm database and reschedule if needed
        checkForMissedAlarms(context)
    }
    
    private fun checkForMissedAlarms(context: Context) {
        // Placeholder: In production, this would:
        // 1. Query for alarms that should have fired but didn't
        // 2. Check if health check alarms are working
        // 3. Trigger any missed alarms immediately
        // 4. Log reliability metrics
    }
}

/**
 * Charging state receiver that can trigger alarm checks when device is plugged in.
 * Some OEMs are less aggressive when the device is charging.
 */
@AndroidEntryPoint
class OEMChargingReceiver : BroadcastReceiver() {
    
    companion object {
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            
            val receiver = OEMChargingReceiver()
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                // Device is charging - OEMs are typically less aggressive
                // We could use this to verify alarm scheduling
                onPowerConnected(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Device is on battery - may need extra protection
                onPowerDisconnected(context)
            }
        }
    }
    
    private fun onPowerConnected(context: Context) {
        // Verify alarms are scheduled correctly
        // OEMs often relax restrictions when charging
    }
    
    private fun onPowerDisconnected(context: Context) {
        // Ensure aggressive protection is active
        // May need to start foreground service if not already running
    }
}

/**
 * Helper to register all OEM broadcast receivers at app startup.
 */
object OEMBroadcastReceivers {
    
    fun registerAll(context: Context) {
        OEMTimeTickReceiver.register(context)
        OEMChargingReceiver.register(context)
        // OEMScreenOnReceiver is registered per-alarm as needed
    }
}
