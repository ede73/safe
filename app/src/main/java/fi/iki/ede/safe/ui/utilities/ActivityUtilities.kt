package fi.iki.ede.safe.ui.utilities

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat


fun ComponentActivity.startActivityForResults(
    result: ActivityResultCallback<ActivityResult>,
) = registerActivityForResults(
    ActivityResultContracts.StartActivityForResult(),
    result,
    ::registerForActivityResult
)


fun PreferenceFragmentCompat.startActivityForResults(
    result: ActivityResultCallback<ActivityResult>,
) = registerActivityForResults(
    ActivityResultContracts.StartActivityForResult(),
    result,
    ::registerForActivityResult
)

//fun <I, O> registerActivityForResults(
//    contract: ActivityResultContract<I, O>,
//    callback: ActivityResultCallback<O>,
//    registerFunction: (ActivityResultContract<I, O>, ActivityResultCallback<O>) -> ActivityResultLauncher<I>
//) =
//    registerFunction(contract, callback)

fun registerActivityForResults(
    contract: ActivityResultContract<Intent, ActivityResult>,
    callback: ActivityResultCallback<ActivityResult>,
    registerFunction: (ActivityResultContract<Intent, ActivityResult>, ActivityResultCallback<ActivityResult>) -> ActivityResultLauncher<Intent>
) = registerFunction(contract, callback)
