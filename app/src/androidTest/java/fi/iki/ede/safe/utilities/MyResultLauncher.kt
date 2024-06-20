package fi.iki.ede.safe.utilities

import android.app.Activity.RESULT_CANCELED
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import fi.iki.ede.safe.ui.TestTag
import fi.iki.ede.safe.ui.utilities.registerActivityForResults
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify

class MyResultLauncher(
    private val testTag: TestTag,
    private val callback: ActivityResultCallback<ActivityResult>
) : ActivityResultLauncher<Intent>() {

    init {
        require(launchedIntents.keys.none { it.first == testTag }) {
            "Each and TestTag must be unique, else we can't identify who's launching and what"
        }

        launchedIntents[Pair(testTag, callback)] = mutableListOf()
    }

    override fun launch(i: Intent, options: ActivityOptionsCompat?) {
        (launchedIntents[Pair(testTag, callback)] as MutableList<Intent>).add(i)
        val result = if (lr.containsKey(testTag)) {
            lr[testTag]!!(testTag)
        } else
            ActivityResult(RESULT_CANCELED, null)
        callback.onActivityResult(result)
    }

    override fun launch(i: Intent) = launch(i, null)

    override val contract: ActivityResultContract<Intent, *>
        get() = ActivityResultContracts.StartActivityForResult()

    override fun unregister() {}

    companion object {
        fun afterEachTest() {
            launchedIntents.clear()
            lr.clear()
        }

        private val launchedIntents: MutableMap<Pair<TestTag, ActivityResultCallback<ActivityResult>>, List<Intent>> =
            mutableMapOf()
        private val lr: MutableMap<TestTag, (tag: TestTag) -> ActivityResult> = mutableMapOf()

        fun registerTestLaunchResult(testTag: TestTag, result: (tag: TestTag) -> ActivityResult) {
            lr[testTag] = result
        }

        fun getLaunchedIntentsAndCallback(testTag: TestTag) =
            launchedIntents.keys.first { it.first == testTag }.let {
                Pair(it.second, launchedIntents[it]!!)
            }

//        fun clearLaunchedIntents(testTag: TestTag) =
//            launchedIntents.remove(launchedIntents.keys.first { it.first == testTag })

        fun beforeClassJvmStaticSetup() {
            mockkStatic(::registerActivityForResults)
            // Only place to mock these is here, when @Test starts, it is too late, activity already initialized
            every {
//            registerActivityForResults<Intent, ActivityResult>(
                // YES! The TestTag is REALLY necessary! Do not remove!
                registerActivityForResults(any(), any(), any(), any())
            } answers {
                val instance = it.invocation
                val testTag = firstArg<TestTag>()
                val contract = secondArg<ActivityResultContract<Intent, ActivityResult>>()
                val callback = thirdArg<ActivityResultCallback<ActivityResult>>()
                val register =
                    lastArg<(ActivityResultContract<Intent, ActivityResult>, ActivityResultCallback<ActivityResult>) -> ActivityResultLauncher<Intent>>()
                MyResultLauncher(testTag, callback)
            }
        }

        fun fetchResults() {
            verify { registerActivityForResults(any(), any(), any(), any()) }
        }
    }
}