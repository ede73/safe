package fi.iki.ede.safe.ui.activities

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.autolock.AutolockingFeaturesImpl
import fi.iki.ede.crypto.keystore.MockKeyStoreHelper
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.*
import fi.iki.ede.datamodel.DataModel
import fi.iki.ede.gpmdatamodel.GPMDataModel
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.BuildConfig
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.ui.composable.CategoryListScreenCompose
import fi.iki.ede.safe.ui.composable.DualModePreview
import fi.iki.ede.safe.ui.sync.ContinuousAutoSyncCoordinator
import fi.iki.ede.safe.ui.sync.DevicePlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime


@ExperimentalTime
class CategoryListScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Preferences.setNotificationPermissionDenied(true)
        }
    }

    @ExperimentalFoundationApi
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
        DevicePlatformInfo.appVersion = BuildConfig.VERSION_NAME
        val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
        DataModel.onDataChanged = {
            ContinuousAutoSyncCoordinator.instance.onLocalDataChanged(clusterSecret)
        }
        ContinuousAutoSyncCoordinator.instance.onDataMutatedBySync = {
            CoroutineScope(Dispatchers.Main).launch {
                DataModel.loadFromDatabase {
                    GPMDataModel.loadFromDatabase()
                }
            }
        }
        ContinuousAutoSyncCoordinator.instance.start(authToken = clusterSecret)
        setContent { CategoryListScreenCompose(DataModel.categoriesStateFlow) }
    }

    override fun onResume() {
        super.onResume()
        val clusterSecret = Preferences.getVaultClusterSecret() ?: "DEFAULT_VAULT_CLUSTER"
        ContinuousAutoSyncCoordinator.instance.start(authToken = clusterSecret)
    }

    override fun onDestroy() {
        super.onDestroy()
        ContinuousAutoSyncCoordinator.instance.stop()
    }
}

@DualModePreview
@Composable
@ExperimentalTime
@ExperimentalFoundationApi
fun CategoryScreenPreview() {
    MockKeyStoreHelper.init()
    val flow = listOf(DecryptableCategoryEntry().apply {
        encryptedName = "Android".encrypt()
    }, DecryptableCategoryEntry().apply {
        encryptedName = "iPhone".encrypt()
    })
    CategoryListScreenCompose(MutableStateFlow(flow))
}