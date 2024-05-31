package fi.iki.ede.safe.ui.activities

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment


fun ComponentActivity.startActivityForResults(result: ActivityResultCallback<ActivityResult>) =
    registerActivityForResults(
        ActivityResultContracts.StartActivityForResult(),
        result,
        ::registerForActivityResult
    )


fun Fragment.startActivityForResults(result: ActivityResultCallback<ActivityResult>) =
    registerActivityForResults(
        ActivityResultContracts.StartActivityForResult(),
        result,
        ::registerForActivityResult
    )

private fun <I, O> registerActivityForResults(
    contract: ActivityResultContract<I, O>,
    callback: ActivityResultCallback<O>,
    registerFunction: (ActivityResultContract<I, O>, ActivityResultCallback<O>) -> ActivityResultLauncher<I>
): ActivityResultLauncher<I> =
    registerFunction(contract, callback)
