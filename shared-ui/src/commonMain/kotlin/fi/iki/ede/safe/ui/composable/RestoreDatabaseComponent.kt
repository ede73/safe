package fi.iki.ede.safe.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import fi.iki.ede.backup.RestoreDatabase
import fi.iki.ede.crypto.Password
import fi.iki.ede.db.DBHelperFactory
import fi.iki.ede.preferences.Preferences
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Source
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
@ExperimentalTime
fun RestoreDatabaseComponent(
    processedPasswords: MutableIntState,
    processedCategories: MutableIntState,
    processedMessage: MutableState<String>,
    backupPassword: Password,
    backupSource: Source,
    passwordLogin: (Password) -> Boolean,
    linkSaveGPMAndSiteEntry: (Long, Long) -> Unit,
    addSavedGPM: (fi.iki.ede.gpm.model.SavedGPM) -> Unit,
    onFinished: (passwords: Int, ex: Exception?) -> Unit,
    verifyUserWantForOldBackup: (Instant, Instant) -> Boolean
) {
    val dbHelper = DBHelperFactory.getDBHelper()

    LaunchedEffect(Unit) {
        launch(Dispatchers.Default + CoroutineName("DATABASE_RESTORATION")) {
            try {
                val passwords = RestoreDatabase().doRestore(
                    backupSource = backupSource,
                    userPassword = backupPassword,
                    dbHelper = dbHelper,
                    lastBackupDone = Preferences.getLastBackupTime(),
                    linkSaveGPMAndSiteEntry = linkSaveGPMAndSiteEntry,
                    addSavedGPM = addSavedGPM,
                    passwordLogin = passwordLogin,
                    reportProgress = { categories: Int?, passwords: Int?, message: String? ->
                        categories?.let {
                            processedCategories.intValue = it
                        }
                        passwords?.let {
                            processedPasswords.intValue = it
                        }
                        message?.let {
                            processedMessage.value = it
                        }
                    },
                    verifyUserWantForOldBackup = verifyUserWantForOldBackup
                )
                onFinished(passwords, null)
            } catch (ex: Exception) {
                ex.printStackTrace()
                onFinished(0, ex)
            }
        }
    }
}
