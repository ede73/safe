package fi.iki.ede.safe.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.iki.ede.autolock.AutoLockingBaseComponentActivity
import fi.iki.ede.crypto.IVCipherText
import fi.iki.ede.crypto.keystore.KeyStoreHelperFactory
import fi.iki.ede.crypto.support.encrypt
import fi.iki.ede.cryptoobjects.DecryptableSiteEntry
import fi.iki.ede.db.DBID
import fi.iki.ede.preferences.Preferences
import fi.iki.ede.safe.R
import fi.iki.ede.safe.model.DataModel
import fi.iki.ede.safe.notifications.SetupNotifications
import fi.iki.ede.safe.password.PasswordGenerator
import fi.iki.ede.safe.ui.AutolockingFeaturesImpl
import fi.iki.ede.safe.ui.composable.SiteEntryEditCompose
import fi.iki.ede.safe.ui.models.EditableSiteEntry
import fi.iki.ede.safe.ui.models.EditingSiteEntryViewModel
import fi.iki.ede.safe.ui.utilities.MeasureTime
import fi.iki.ede.theme.SafeTheme
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds


class SiteEntryEditScreen :
    AutoLockingBaseComponentActivity(AutolockingFeaturesImpl) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MeasureTime("SiteEntryEditScreen.onCreate").apply {
            val encrypter = KeyStoreHelperFactory.getEncrypter()
            val viewModel: EditingSiteEntryViewModel by viewModels()
            lap("view model")

            if (savedInstanceState == null) {
                if (intent.hasExtra(SITE_ENTRY_ID)) {
                    // Edit a password
                    val siteEntryId = intent.getLongExtra(SITE_ENTRY_ID, -1L)
                    require(siteEntryId != -1L) { "Password must be value and exist" }
                    viewModel.editSiteEntry(DataModel.getSiteEntry(siteEntryId))
                    lap("extract site entry id")
                } else if (intent.hasExtra(CATEGORY_ID)) {
                    // Add a new siteentry
                    val categoryId = intent.getLongExtra(CATEGORY_ID, -1L)
                    require(categoryId != -1L) { "Category must be a value and exist" }

                    val passwordLength =
                        this@SiteEntryEditScreen.resources.getInteger(R.integer.password_default_length)
                    // new password - auto gen proper pwd
                    val newPassword = PasswordGenerator.genPassword(
                        passUpper = true,
                        passLower = true,
                        passNum = true,
                        passSymbols = true,
                        length = passwordLength
                    )

                    viewModel.addPassword(
                        newPassword.encrypt(encrypter),
                        categoryId,
                        Preferences.getDefaultUserName().encrypt(encrypter)
                    )
                    lap("make new password entry")
                } else {
                    require(true) { "Must have siteEntry or category ID" }
                }
            }

            val editingSiteEntryId = intent.getLongExtra(SITE_ENTRY_ID, -1L)

            SetupNotifications.setup(this@SiteEntryEditScreen)
            lap("set up notifications")

            setContent {
                SiteEntryEditCompose(
                    viewModel,
                    editingSiteEntryId.takeIf { it != -1L },
                    ::resolveEditsAndChangedSiteEntry,
                    ::setResult,
                    ::finishActivity
                )
                // let's ensure we log in debug mode a proper error if screen load is too slow
                // (bit rot had set it)
                lap("launch SiteEntryEditCompose", 600.milliseconds)
            }
        }
    }

    private fun finishActivity() {
        finish()
    }

    private fun resolveEditsAndChangedSiteEntry(
        editingPasswordId: DBID?,
        edits: EditableSiteEntry,
    ) = if (editingPasswordId != null) {
        val originalSiteEntry = DataModel.getSiteEntry(editingPasswordId)
        wasSiteEntryChanged(
            edits,
            originalSiteEntry
        ) to !originalSiteEntry.isSamePassword(edits.password)
    } else {
        // We're adding new password, so consider changed
        true to true
    }

    private fun wasSiteEntryChanged(
        edits: EditableSiteEntry,
        originalSiteEntry: DecryptableSiteEntry
    ) = !originalSiteEntry.isSame(
        edits.description,
        edits.website,
        edits.username,
        edits.password,
        edits.passwordChangedDate,
        edits.note,
        edits.plainPhoto,
        edits.plainExtension
    )

    companion object {
        const val SITE_ENTRY_ID = "password_id"
        const val CATEGORY_ID = "category_id"
        const val TAG = "PasswordEntryScreen"
    }
}

@Preview(showBackground = true)
@Composable
fun SiteEntryScreenPreview() {
    KeyStoreHelperFactory.encrypterProvider = { IVCipherText(it, it) }
    KeyStoreHelperFactory.decrypterProvider = { it.cipherText }
    val entry = DecryptableSiteEntry(1)
    entry.id = 1
    entry.categoryId = 1
    entry.note = IVCipherText(byteArrayOf(), "note\nmay have\nmultiple lines".toByteArray())
    entry.description = IVCipherText(byteArrayOf(), "Secret site".toByteArray())
    entry.password = IVCipherText(byteArrayOf(), "password".toByteArray())
    entry.passwordChangedDate = ZonedDateTime.now()
    entry.username = IVCipherText(byteArrayOf(), "username".toByteArray())
    entry.website = IVCipherText(byteArrayOf(), "website".toByteArray())
    //entry.photo=
    class FakeEditingSiteViewModel : EditingSiteEntryViewModel()

    val fakeViewModel = FakeEditingSiteViewModel().apply {
        // Set up the ViewModel with test data as needed
        editSiteEntry(entry)
    }
    SafeTheme {
        SiteEntryEditCompose(
            fakeViewModel,
            1,
            { _, _ -> true to true },
            { _, _ -> },
            {},
            skipForPreviewToWork = true
        )
    }
}

