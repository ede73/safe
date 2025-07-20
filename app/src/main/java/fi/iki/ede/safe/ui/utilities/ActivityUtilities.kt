package fi.iki.ede.safe.ui.utilities

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import fi.iki.ede.safe.ui.TestTag


fun ComponentActivity.startActivityForResults(
    testTag: TestTag, // used in MyResultLauncher.kt, mock, do not remove!
    result: ActivityResultCallback<ActivityResult>,
) = registerActivityForResults(
    testTag,
    ActivityResultContracts.StartActivityForResult(),
    result,
    ::registerForActivityResult
)


fun PreferenceFragmentCompat.startActivityForResults(
    testTag: TestTag, // used in MyResultLauncher.kt, mock, do not remove!
    result: ActivityResultCallback<ActivityResult>,
) = registerActivityForResults(
    testTag,
    ActivityResultContracts.StartActivityForResult(),
    result,
    ::registerForActivityResult
)

fun registerActivityForResults(
    testTag: TestTag, // used in MyResultLauncher.kt, mock, do not remove!
    contract: ActivityResultContract<Intent, ActivityResult>,
    callback: ActivityResultCallback<ActivityResult>,
    registerFunction: (ActivityResultContract<Intent, ActivityResult>, ActivityResultCallback<ActivityResult>) -> ActivityResultLauncher<Intent>
) = registerFunction(contract, callback)
