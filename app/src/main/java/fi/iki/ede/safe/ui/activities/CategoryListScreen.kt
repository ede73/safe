package fi.iki.ede.safe.ui.activities

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class CategoryListScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            fi.iki.ede.preferences.Preferences.setNotificationPermissionDenied(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (fi.iki.ede.preferences.Preferences.getNotificationPermissionRequired() &&
            !fi.iki.ede.preferences.Preferences.getNotificationPermissionDenied()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        SetupNotifications.setup(this)
        setContent { CategoryListScreenCompose(DataModel.categoriesStateFlow) }
    }
}

@Composable
@Suppress("FlowOperatorInvokedInComposition")
private fun CategoryListScreenCompose(
    flow: StateFlow<List<DecryptableCategoryEntry>> = MutableStateFlow(
        emptyList()
    )
) {
    val coroutineScope = rememberCoroutineScope()
    // TODO: Either new kotlin, coroutines or both, this is a linter error now
    val categoriesState by flow.map { categories -> categories.sortedBy { it.plainName.lowercase() } }
        .collectAsState(initial = emptyList())
    var displayAddCategoryDialog by remember { mutableStateOf(false) }

    SafeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TopActionBar(
                    onAddRequested = {
                        displayAddCategoryDialog = true
                    },
                )
                if (displayAddCategoryDialog) {
                    val encrypter = KeyStoreHelperFactory.getEncrypter()
                    AddOrEditCategory(
                        textId = R.string.category_list_edit_category,
                        categoryName = "",
                        onSubmit = {
                            if (!TextUtils.isEmpty(it)) {
                                val entry = DecryptableCategoryEntry().apply {
                                    encryptedName = encrypter(it.toByteArray())
                                }
                                coroutineScope.launch {
                                    DataModel.addOrEditCategory(entry)
                                }
                            }
                            displayAddCategoryDialog = false
                        })
                }
                CategoryList(categoriesState)
            }
        }
    }
}

@Preview(showBackground = true)
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