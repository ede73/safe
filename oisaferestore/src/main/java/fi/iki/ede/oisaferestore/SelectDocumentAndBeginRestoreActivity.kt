package fi.iki.ede.oisaferestore

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fi.iki.ede.crypto.Password
import fi.iki.ede.safe.R
import fi.iki.ede.safe.backupandrestore.ExportConfig
import fi.iki.ede.safe.db.DBHelperFactory
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.splits.IntentManager
import fi.iki.ede.safe.ui.activities.AskBackupPasswordAndCommence
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.runBlocking

class SelectDocumentAndBeginRestoreActivity : AutolockingBaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1) pick document
        // 2) begin restoration

        val context = this
        setContent {
            var selectedDoc by remember { mutableStateOf<Uri?>(null) }
            val selectDocument =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        selectedDoc = result.data!!.data!!
                    }
                }
            val processedPasswords = remember { mutableIntStateOf(0) }
            val processedCategories = remember { mutableIntStateOf(0) }
            val processedMessage = remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                selectDocument.launch(ExportConfig.getOpenDocumentIntent())
            }

            if (selectedDoc != null) {
                AskBackupPasswordAndCommence(
                    processedPasswords,
                    processedCategories,
                    processedMessage,
                    selectedDoc!!,
                    this,
                    ::avertInactivity
                ) { backupPassword ->
                    RestoreOISafeBackup(
                        context,
                        backupPassword,
                        selectedDoc!!,
                        onFinished = { restoredPasswords, ex ->
                            // YES, we could have 0 passwords, but 1 category
                            if (ex == null) {
                                // TODO: MAKE ASYNC
                                runBlocking {
                                    DataModel.loadFromDatabase()
                                }
                                processedMessage.value = context.getString(
                                    R.string.restore_screen_restored,
                                    restoredPasswords
                                )
                                IntentManager.startCategoryScreen(context)
                                context.setResult(RESULT_OK)
                                context.finish()
                            } else {
                                processedMessage.value =
                                    context.getString(R.string.restore_screen_restore_failed)
                                IntentManager.startCategoryScreen(context)
                                context.setResult(RESULT_CANCELED)
                                context.finish()
                            }
                        }
                    )
                    selectedDoc = null
                }
            }
        }
    }
}

@Composable
fun RestoreOISafeBackup(
    context: SelectDocumentAndBeginRestoreActivity,
    backupPassword: Password,
    selectedDoc: Uri,
    onFinished: (passwords: Int, ex: Exception?) -> Unit
) {
    val stream = LocalContext.current.contentResolver.openInputStream(selectedDoc)!!
    val dbHelper = DBHelperFactory.getDBHelper(LocalContext.current)

    try {
        val passwords = restoreOiSafeDump(
            context,
            dbHelper,
            backupPassword,
            stream
        )
        onFinished(passwords, null)
    } catch (ex: Exception) {
        onFinished(0, ex)
    }
}
