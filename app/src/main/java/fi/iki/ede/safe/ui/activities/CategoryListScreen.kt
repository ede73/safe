package fi.iki.ede.safe.ui.activities

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.cryptoobjects.DecryptableCategoryEntry
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.ui.composable.CategoryListScreenCompose
import fi.iki.ede.safe.ui.composable.DualModePreview
import kotlinx.coroutines.flow.MutableStateFlow


class CategoryListScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Preferences.setNotificationPermissionDenied(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Preferences.getNotificationPermissionRequired() &&
            !Preferences.getNotificationPermissionDenied()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        SetupNotifications.setup(this)
        setContent { CategoryListScreenCompose(DataModel.categoriesStateFlow) }
    }
}

@DualModePreview
@Composable
fun CategoryScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }

    val flow = listOf(DecryptableCategoryEntry().apply {
        encryptedName = KeyStoreHelperFactory.getEncrypter()("Android".toByteArray())
    }, DecryptableCategoryEntry().apply {
        encryptedName = KeyStoreHelperFactory.getEncrypter()("iPhone".toByteArray())
    })
    CategoryListScreenCompose(MutableStateFlow(flow))
}