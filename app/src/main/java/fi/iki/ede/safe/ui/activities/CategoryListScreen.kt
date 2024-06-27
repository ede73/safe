package fi.iki.ede.safe.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.core.app.ActivityCompat
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.model.DecryptableCategoryEntry
import fi.iki.ede.safe.model.Preferences
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.ui.composable.AddOrEditCategory
import fi.iki.ede.safe.ui.composable.CategoryList
import fi.iki.ede.safe.ui.composable.TopActionBar
import fi.iki.ede.safe.ui.theme.SafeTheme
import fi.iki.ede.safe.ui.utilities.AutolockingBaseComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class CategoryListScreen : AutolockingBaseComponentActivity() {
    private val MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 666
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Preferences.getNotificationPermissionRequired() &&
            !Preferences.getNotificationPermissionDenied()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission()
            }
        }
        SetupNotifications.setup(this)
        setContent { CategoryListScreenCompose(DataModel.categoriesStateFlow) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS -> {
                if ((grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                    Preferences.setNotificationPermissionDenied(true)
                }
                return
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS
            )
        }
    }
}

@Composable
private fun CategoryListScreenCompose(
    flow: StateFlow<List<DecryptableCategoryEntry>> = MutableStateFlow(
        emptyList()
    )
) {
    val coroutineScope = rememberCoroutineScope()
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