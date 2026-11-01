package com.sjunka.spectrumar.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay

enum class ArAvailability { SUPPORTED, NEEDS_INSTALL, UNSUPPORTED, UNKNOWN }

object ArSupport {
    fun checkOnce(context: Context): ArAvailability = try {
        when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailability.SUPPORTED
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailability.NEEDS_INSTALL
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArAvailability.UNSUPPORTED
            else -> ArAvailability.UNKNOWN
        }
    } catch (e: Throwable) {
        ArAvailability.UNSUPPORTED
    }

    /** `checkAvailability` can answer "still checking": poll briefly before giving up. */
    suspend fun check(context: Context): ArAvailability {
        repeat(12) {
            val a = checkOnce(context)
            if (a != ArAvailability.UNKNOWN) return a
            delay(180)
        }
        return ArAvailability.UNKNOWN
    }

    /** Opens the Play Store flow for Google Play Services for AR. Returns true when the install was requested. */
    fun requestInstall(activity: Activity): Boolean = try {
        ArCoreApk.getInstance().requestInstall(activity, true) == ArCoreApk.InstallStatus.INSTALL_REQUESTED
    } catch (e: Throwable) {
        false
    }
}
