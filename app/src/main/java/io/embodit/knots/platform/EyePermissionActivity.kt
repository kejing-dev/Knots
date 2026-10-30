package io.embodit.knots.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Transparent helper for the eye-tracking permission `com.picovr.permission.EYE_TRACKING` (SDK doc "Eye tracking":
 * a runtime permission; same helper as Rehearsal Hall). Refused or no dialog: look-to-advance uses the head's direction
 * and the step panel says so.
 */
class EyePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (granted(this)) {
            finish()
            return
        }
        requestPermissions(arrayOf(PERMISSION), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        KnotLog.i("EYE permission granted=${grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED}")
        finish()
    }

    companion object {
        const val PERMISSION = "com.picovr.permission.EYE_TRACKING"
        private const val REQUEST = 53

        fun granted(context: Context): Boolean = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

        fun open(context: Context): Boolean = runCatching { context.startActivity(Intent(context, EyePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
