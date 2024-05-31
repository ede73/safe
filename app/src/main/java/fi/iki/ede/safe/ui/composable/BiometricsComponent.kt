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
import androidx.compose.ui.res.stringResource
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.activities.BiometricsActivity
import fi.iki.ede.safe.ui.testTag

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
        BiometricsActivity.clearBiometricKeys()
        true
    }

    // TODO: Should be remembered and based on preferences (and test case should invoke...)
    val biometricsActivityEnabled = BiometricsActivity.isBiometricEnabled()
    val biometricsRecorded = BiometricsActivity.haveRecordedBiometric()
    var registerBiometrics by remember { mutableStateOf(biometricsActivityEnabled) }

    if (biometricsActivityEnabled && biometricsRecorded && !keystoreMissingMasterkeyAfterBackupRestore) {
        // Actually during login, we could JUST launch the bio verification immediately
        // since biometrics is enabled AND we have previously recorded entry
        bioVerify?.launch(BiometricsActivity.getVerificationIntent(context))
        Button(
            onClick = {
                bioVerify?.launch(BiometricsActivity.getVerificationIntent(context))
            },
            modifier = Modifier.testTag(TestTag.TEST_TAG_BIOMETRICS_BUTTON)
        ) { Text(stringResource(id = R.string.login_with_biometrics)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = registerBiometrics, onCheckedChange = {
                    BiometricsActivity.setBiometricEnabled(it)
                    registerBiometrics = it
                },
                modifier = Modifier.testTag(TestTag.TEST_TAG_BIOMETRICS_CHECKBOX)
            )
            Text(stringResource(id = R.string.biometrics_register))
        }
    }
}