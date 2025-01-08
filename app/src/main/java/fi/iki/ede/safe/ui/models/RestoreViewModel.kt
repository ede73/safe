package fi.iki.ede.safe.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import fi.iki.ede.crypto.Password

class RestoreViewModel : ViewModel() {
    var docUri: Uri? = null

    // TODO: Terrible, move..
    var backupPassword: Password? = null
}
