package org.meshtastic.core.ui.qr

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class MlKitScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(context, MlKitScanningActivity::class.java)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        return if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(EXTRA_SCAN_RESULT)
        } else {
            null
        }
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "scan_result"
    }
}
