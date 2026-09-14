package fi.iki.ede.safe.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import fi.iki.ede.gpmdatamodel.db.GPMDB
import fi.iki.ede.logger.Logger
import fi.iki.ede.safe.cxf.FidoCxfParser
import kotlin.time.ExperimentalTime

private const val TAG = "ExportCredentialsActivity"

class ExportCredentialsActivity : AppCompatActivity() {

    @OptIn(ExperimentalTime::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val savedGPMs = GPMDB.fetchAllSavedGPMsFromDB().toList()
            val cxfPayload = FidoCxfParser.serializeSavedGPMsToCxfPayload(savedGPMs)

            val resultIntent = Intent().apply {
                putExtra("androidx.credentials.providerevents.extra.CXF_PAYLOAD", cxfPayload)
            }
            setResult(Activity.RESULT_OK, resultIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "Exporting credentials via FIDO CXF failed: ${e.message}", e)
            setResult(Activity.RESULT_CANCELED)
        } finally {
            finish()
        }
    }
}
