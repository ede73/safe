package fi.iki.ede.safe.ui.composable

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.activities.Biometrics
import fi.iki.ede.safe.ui.activities.LoginScreen.Companion.TESTTAG_BIOMETRICS_BUTTON
import fi.iki.ede.safe.ui.activities.LoginScreen.Companion.TESTTAG_BIOMETRICS_CHECKBOX

@Composable
fun BiometricsComponent(
    bioVerify: ActivityResultLauncher<Intent>? = null,
) {
    val context = LocalContext.current

    // TODO: Don't allow biometrics is keystore doesn't initialize
    // this situation MIGHT happen when app is fresh installed AND google restored backup preferences
    val keystoreMissingMasterkeyAfterBackupRestore = try {
        val ks = KeyStoreHelperFactory.getKeyStoreHelper()
        false
    } catch (ex: Exception) {
        Biometrics.clearBiometricKeys(context)
        true
    }

    val biometricsEnabled = Biometrics.isBiometricEnabled(context)
    var registerBiometrics by remember { mutableStateOf(biometricsEnabled) }

    if (biometricsEnabled && Biometrics.haveRecordedBiometric(context) && !keystoreMissingMasterkeyAfterBackupRestore) {
        // Actually during login, we could JUST launch the bio verification immediately
        // since biometrics is enabled AND we have previously recorded entry
        bioVerify?.launch(Biometrics.getVerificationIntent(context))
        Button(
            onClick = {
                bioVerify?.launch(Biometrics.getVerificationIntent(context))
            },
            modifier = Modifier.testTag(TESTTAG_BIOMETRICS_BUTTON)
        ) { Text(stringResource(id = R.string.login_with_biometrics)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = registerBiometrics, onCheckedChange = {
                    Biometrics.setBiometricEnabled(context, it)
                    registerBiometrics = it
                },
                modifier = Modifier.testTag(TESTTAG_BIOMETRICS_CHECKBOX)
            )
            Text(stringResource(id = R.string.biometrics_register))
        }
    }
}